# ByteToMessageDecoder


```text
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

    private Cumulator cumulator = MERGE_CUMULATOR;
    ByteBuf cumulation;                               // 接收到的byteBuf数据在一次decode后会有剩余(对方传输的不够多),这些剩余会在下次接收到更多数据后才能继续解析
    
    private boolean singleDecode;
    private boolean first;
}
```

```text
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    
    // 本ChannelInboundHandler只处理ByteBuf类型的数据
    if (msg instanceof ByteBuf) {
        // 标记
        selfFiredChannelRead = true;
        
        // 对象池复用,避免频繁new ArrayList
        CodecOutputList out = CodecOutputList.newInstance();
        try {
            // (如果是第一次读取数据,创建一个空的ByteBuf作为cumulation buffer) 从msg读取字节数据并写入到cumulation, cumulation中的字节数据在decode时会被消费
            first = cumulation == null;
            cumulation = cumulator.cumulate(ctx.alloc(), first ? Unpooled.EMPTY_BUFFER : cumulation, (ByteBuf) msg);
            
            // decode时,消费cumulation中的字节数据,decode为N个用户对象并保存到out中(因为N>=0,所以out是个List)
            callDecode(ctx, cumulation, out);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null && !cumulation.isReadable()) {   // 如果decode已经消费完所有数据,重置read计数numReads,释放cumulation-buffer
                    numReads = 0;
                    try {
                        cumulation.release();
                    } catch (IllegalReferenceCountException e) {
                        throw new IllegalReferenceCountException(getClass().getSimpleName() + "#decode() might have released its input buffer, " + "or passed it down the pipeline without a retain() call, " + "which is not allowed.", e);
                    }
                    cumulation = null;
                } else if (++numReads >= discardAfterReads) {  // 每read16次就整理一下cumulation-buffer空间(如果长时间不清理cumulation-buffer空间,会有大量的已经read过的无用字节在占用存储空间,最终导致OOM)
                    numReads = 0;
                    discardSomeReadBytes();                    // 将cumulation-buffer已经读过的数据清理掉,为cumulation-buffer腾出更多的写空间
                }

                int size = out.size();
                firedChannelRead |= out.insertSinceRecycled();   // 记录是否fire过
                
                // 向后面通知的msg为decode后的用户对象,而非原始的ByteBuf
                fireChannelRead(ctx, out, size);
            } finally {
                // fireChannelRead后需要及时清理并回收out
                out.recycle();
            }
        }
    } else {
        ctx.fireChannelRead(msg);  // 如果无法处理,就将in-bound的msg交给下个ChannelHandler处理
    }
}
```

```text
protected void callDecode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
    try {
        // 因为从cumulation(即in)可能会decode出多个对象,也可能只读部分数据,也可能什么也解不出来,所以需要一个循环控制器
        while (in.isReadable()) {                   // 只要cumulation-buffer还有数据,就尝试decode
            final int outSize = out.size();

            // 如果已有解码结果就立即发送decode出来的对象,防止out列表无限增长
            if (outSize > 0) {
                fireChannelRead(ctx, out, outSize);
                out.clear();
                
                if (ctx.isRemoved()) {  // decode()期间,handler可能被动态移除,如果发生了就立即停止decode(先fireChannelRead后再检查handler是否被移除了.因为在上一个循环中decode后已经检查过handler还未被移除)
                    break;
                }
            }

            int oldInputLength = in.readableBytes();            // 这是防止decode死循环的关键变量,记录decode之前的可读字节数
            
            decodeRemovalReentryProtection(ctx, in, out);       // 💯💯真正调用decode (在decode前后分别检查handler是否被移除)

            // decode完后需要立即检查handler是否被移除(比如HTTP upgrade),(这样在下个循环中才能无需检查直接fireChannelRead)
            if (ctx.isRemoved()) {
                break;
            }

            if (out.isEmpty()) {                             // 如果decode没有产生message
                if (oldInputLength == in.readableBytes()) {     // 如果没有消费任何字节数据,当前数据量不足以支持decode进行下去,需要等待下一次网络read提供更多数据
                    break;
                } else {
                    continue;                                   // 如果消费了字节数据,回到循环开始继续进行decode(直到数据少到不足以支持decode进行下去)
                }
            }
            // 到此处,decode已经产生新的message

            if (oldInputLength == in.readableBytes()) {     // 异常情况:没有消费数据但是decode却产生了message,这是严重bug
                throw new DecoderException(StringUtil.simpleClassName(getClass()) + ".decode() did not read anything but decoded a message.");
            }

            // singleDecode模式(使用场景: HTTP aggregator/protocol switch)
            if (isSingleDecode()) {
                break;                                      // 虽然cumulation-buffer还有数据,当前handler在整个tcp对话中只执行一次decode(后续该handler会从pipeline被移除)
            }
        }
    } catch (DecoderException e) {
        throw e;
    } catch (Exception cause) {
        throw new DecoderException(cause);
    }
}



callDecode的状态机
             +-------------------+
             |  in.isReadable()  |
             +---------+---------+
                       |
                       v
                call decode()
                       |
         +-------------+-------------+
         |                           |
   产生 message                没产生 message
         |                           |
         v                           v
  fireChannelRead()          是否消费数据
         |                    /        \
         v                  是          否
     continue            continue       break
```

```text
final void decodeRemovalReentryProtection(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
    decodeState = STATE_CALLING_CHILD_DECODE;  // 通过设置decodeState以及handlerRemoved的延迟删除handler机制,这样在decode中执行pipeline.remove(this)也是安全的
    try {
        // 用户自定义decode逻辑
        decode(ctx, in, out);
    } finally {
        boolean removePending = decodeState == STATE_HANDLER_REMOVED_PENDING;
        decodeState = STATE_INIT;                                                      // 结束decode(),handler恢复到正常状态                                      
        
        // 处理handler被移除的收尾工作
        if (removePending) {
            fireChannelRead(ctx, out, out.size());      // 将已经decode得到的message交给pipeline中的后续handler处理
            out.clear();
            handlerRemoved(ctx);                        // handlerRemoved会处理cumulation-buffer残余的数据
        }
    }
}


decodeState的三个状态
状态	                                含义
STATE_INIT	                        正常状态
STATE_CALLING_CHILD_DECODE	        正在执行decode()
STATE_HANDLER_REMOVED_PENDING	    在执行decode()期间,发生了handler被移除

handler未发生移除且未执行decode()方法前,状态是STATE_INIT
handler执行decode()方法时,状态是STATE_CALLING_CHILD_DECODE
handler被移除时,状态是STATE_HANDLER_REMOVED_PENDING
```


```text
public final void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
    if (decodeState == STATE_CALLING_CHILD_DECODE) {
        decodeState = STATE_HANDLER_REMOVED_PENDING;        // 如果handler正在执行decode()方法,只是标记一个移除flag.handler在执行完decode()方法后会去检查该标记,然后做相应的收尾工作,这样就实现了延迟删除handler机制
        return;
    }
    
    // handler此时没有执行decode()方法
    ByteBuf buf = cumulation;
    if (buf != null) {
        cumulation = null;                                 // Directly set this to null, so we are sure we not access it in any other method here anymore.
        numReads = 0;
        int readable = buf.readableBytes();
        if (readable > 0) {
            ctx.fireChannelRead(buf);                      // 在handler被移除时,还有未消费的数据,需要将这些数据被其他handler读取到
            ctx.fireChannelReadComplete();
        } else {
            buf.release();
        }
    }
    handlerRemoved0(ctx);                                  // 💯💯💯Gets called after the ByteToMessageDecoder was removed from the actual context and it doesn't handle events anymore.
}
```


























