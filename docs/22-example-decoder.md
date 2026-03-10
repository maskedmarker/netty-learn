# example-decoder

## ByteToMessageDecoder

```text
public abstract class ByteToMessageDecoder extends ChannelInboundHandlerAdapter {

}
```

```text
public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    
    // 本ChannelInboundHandler只处理ByteBuf类型的数据
    if (msg instanceof ByteBuf) {
        selfFiredChannelRead = true;
        CodecOutputList out = CodecOutputList.newInstance();
        
        try {
            first = cumulation == null;
            cumulation = cumulator.cumulate(ctx.alloc(), first ? Unpooled.EMPTY_BUFFER : cumulation, (ByteBuf) msg);
            callDecode(ctx, cumulation, out);
        } catch (DecoderException e) {
            throw e;
        } catch (Exception e) {
            throw new DecoderException(e);
        } finally {
            try {
                if (cumulation != null && !cumulation.isReadable()) {
                    numReads = 0;
                    try {
                        cumulation.release();
                    } catch (IllegalReferenceCountException e) {
                        //noinspection ThrowFromFinallyBlock
                        throw new IllegalReferenceCountException(
                                getClass().getSimpleName() + "#decode() might have released its input buffer, " +
                                        "or passed it down the pipeline without a retain() call, " +
                                        "which is not allowed.", e);
                    }
                    cumulation = null;
                } else if (++numReads >= discardAfterReads) {
                    // We did enough reads already try to discard some bytes, so we not risk to see a OOME.
                    // See https://github.com/netty/netty/issues/4275
                    numReads = 0;
                    discardSomeReadBytes();
                }

                int size = out.size();
                firedChannelRead |= out.insertSinceRecycled();
                fireChannelRead(ctx, out, size);
            } finally {
                out.recycle();
            }
        }
    } else {
        ctx.fireChannelRead(msg);  // 如果无法处理,就将in-bound的msg交给下个ChannelHandler处理
    }
}
```

## MessageToByteEncoder


























