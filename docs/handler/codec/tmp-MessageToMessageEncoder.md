# MessageToMessageEncoder

如果一个write请求中的数据可以encode为多个message,那么这多个message都被pipeline处理完才可以触发write请求的promise已完成.

```text
public abstract class MessageToMessageEncoder<I> extends ChannelOutboundHandlerAdapter {
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        CodecOutputList out = null;
        try {
            if (acceptOutboundMessage(msg)) {
                out = CodecOutputList.newInstance();
                @SuppressWarnings("unchecked")
                I cast = (I) msg;
                try {
                    encode(ctx, cast, out);
                } catch (Throwable th) {
                    ReferenceCountUtil.safeRelease(cast);
                    PlatformDependent.throwException(th);
                }
                ReferenceCountUtil.release(cast);

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
            if (out != null) {
                try {
                    final int sizeMinusOne = out.size() - 1;
                    if (sizeMinusOne == 0) {                                                                   // 如果一个write请求中的数据只能encode为一个message,这里处理promise比较简单
                        ctx.write(out.getUnsafe(0), promise);
                    } else if (sizeMinusOne > 0) {                                                             // 如果一个write请求中的数据可以encode为多个message,处理promise稍微复杂点
                        // Check if we can use a voidPromise for our extra writes to reduce GC-Pressure
                        // See https://github.com/netty/netty/issues/2525
                        if (promise == ctx.voidPromise()) {                                                       //  voidPromise不用合并promise
                            writeVoidPromise(ctx, out);
                        } else {
                            writePromiseCombiner(ctx, out, promise);                                             // 如果一个write请求中的数据可以encode为多个message,那么这多个message都被pipeline处理完才可以触发write请求的promise已完成
                        }
                    }
                } finally {
                    out.recycle();
                }
            }
        }
    }
}
```

```text
private static void writePromiseCombiner(ChannelHandlerContext ctx, CodecOutputList out, ChannelPromise promise) {
    final PromiseCombiner combiner = new PromiseCombiner(ctx.executor());
    for (int i = 0; i < out.size(); i++) {
        combiner.add(ctx.write(out.getUnsafe(i)));
    }
    combiner.finish(promise);                       // combiner监听其容纳的所有future,当最后一个future done的时候才会触发入参promise的trySuccess/tryFailure
}
```

```text
private static void writeVoidPromise(ChannelHandlerContext ctx, CodecOutputList out) {
    final ChannelPromise voidPromise = ctx.voidPromise();
    for (int i = 0; i < out.size(); i++) {
        ctx.write(out.getUnsafe(i), voidPromise);
    }
}
```