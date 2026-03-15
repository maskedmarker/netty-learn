# HttpObjectEncoder

需要先理解http报文要求,再看代码.

```text
public abstract class HttpObjectEncoder<H extends HttpMessage> extends MessageToMessageEncoder<Object> {
    
    // HttpObjectEncoder是需要有状态的
    // 用来记录处理的http消息时的状态 
    private int state = ST_INIT;
    
    private static final int ST_INIT = 0;                     // 未开始处理
    private static final int ST_CONTENT_NON_CHUNK = 1;        // http消息是非分块传输
    private static final int ST_CONTENT_CHUNK = 2;            // http消息是分块传输
    private static final int ST_CONTENT_ALWAYS_EMPTY = 3;     // http消息的content是空
    
    
    
    // 没有复用MessageToMessageEncoder.write逻辑,完全重写
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        try {
            if (acceptOutboundMessage(msg)) {
                encode(ctx, msg, out);
                if (out.isEmpty()) {
                    throw new EncoderException(
                            StringUtil.simpleClassName(this) + " must produce at least one message.");
                }
            } else {
                ctx.write(msg, promise);
            }
        } catch (EncoderException e) {
            throw e;
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            writeOutList(ctx, out, promise);
        }
    }
}
```

```text
protected void encode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
    // fast-path for common idiom that doesn't require class-checks
    if (msg == Unpooled.EMPTY_BUFFER) {
        out.add(Unpooled.EMPTY_BUFFER);
        return;
    }
    
    // The reason why we perform instanceof checks in this order, by duplicating some code and without relying on ReferenceCountUtil::release as a generic release mechanism, is https://bugs.openjdk.org/browse/JDK-8180450.
    // https://github.com/netty/netty/issues/12708 contains more detail re how the previous version of this code was interacting with the JIT instanceof optimizations.
    if (msg instanceof FullHttpMessage) {                // 一个FullHttpMessage对象包含了完整的http报文的全部数据.(而非chunked分块传输)
        encodeFullHttpMessage(ctx, msg, out);
        return;
    }
    
    // 到此处,msg要么是分块传输,要么就是用户自己自定义需要而造的HttpMessage
    if (msg instanceof HttpMessage) {
        final H m;
        try {
            m = (H) msg;
        } catch (Exception rethrow) {
            ReferenceCountUtil.release(msg);
            throw rethrow;
        }
        if (m instanceof LastHttpContent) {                    // 分块传输:最后一块内容
            encodeHttpMessageLastContent(ctx, m, out);
        } else if (m instanceof HttpContent) {                 //分块传输:非最后一块内容(第一块或中间块)
            encodeHttpMessageNotLastContent(ctx, m, out);
        } else {
            encodeJustHttpMessage(ctx, m, out);                // 普通HttpMessage
        }
    } else {
        encodeNotHttpMessageContentTypes(ctx, msg, out);
    }
}
```