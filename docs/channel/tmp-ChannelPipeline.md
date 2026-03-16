# ChannelPipeline

ChannelPipeline的主要实现类是DefaultChannelPipeline

## addLast

```text
private ChannelPipeline internalAdd(EventExecutorGroup group, String name, ChannelHandler handler, String baseName, AddStrategy addStrategy) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) {
        checkMultiplicity(handler);
        name = filterName(name, handler);

        newCtx = newContext(group, name, handler);               // addLast是创建handlerContext

        switch (addStrategy) {
            case ADD_FIRST:
                addFirst0(newCtx);
                break;
            case ADD_LAST:
                addLast0(newCtx);
                break;
            case ADD_BEFORE:
                addBefore0(getContextOrDie(baseName), newCtx);
                break;
            case ADD_AFTER:
                addAfter0(getContextOrDie(baseName), newCtx);
                break;
            default:
                throw new IllegalArgumentException("unknown add strategy: " + addStrategy);
        }

        // If the registered is false it means that the channel was not registered on an eventLoop yet.
        // In this case we add the context to the pipeline and add a task that will call ChannelHandler.handlerAdded(...) once the channel is registered.
        if (!registered) {                                      // 如果此时channel还未注册
            newCtx.setAddPending();
            callHandlerCallbackLater(newCtx, true);             // 💯💯💯等handler的channel注册完成后,才调用handlerAdded
            return this;
        }

        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop()) {
            callHandlerAddedInEventLoop(newCtx, executor);
            return this;
        }
    }
    
    callHandlerAdded0(newCtx);                                    // handler的handlerContext加入到pipeline后,回到handler的handlerAdded方法
    return this;
}
```