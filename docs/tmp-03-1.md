
ChannelHandlerContext
```text
Enables a ChannelHandler to interact with its ChannelPipeline and other handlers. 
Among other things a handler can notify the next ChannelHandler in the ChannelPipeline as well as modify the ChannelPipeline it belongs to dynamically.


1. Notify
You can notify the closest handler in the same ChannelPipeline by calling one of the various methods provided here. Please refer to ChannelPipeline to understand how an event flows.

2. Modifying a pipeline
You can get the ChannelPipeline your handler belongs to by calling pipeline(). A non-trivial application could insert, remove, or replace handlers in the pipeline dynamically at runtime.

3. Retrieving for later use
You can keep the ChannelHandlerContext for later use, such as triggering an event outside the handler methods, even from a different thread.

4. Storing stateful information
attr(AttributeKey) allow you to store and access stateful information that is related with a ChannelHandler / Channel and its context. 
Please refer to ChannelHandler to learn various recommended ways to manage stateful information.

5. A handler can have more than one ChannelHandlerContext
Please note that a ChannelHandler instance can be added to more than one ChannelPipeline. 
It means a single ChannelHandler instance can have more than one ChannelHandlerContext and therefore the single instance can be invoked with different ChannelHandlerContexts if it is added to one or more ChannelPipelines more than once. 
Also note that a ChannelHandler that is supposed to be added to multiple ChannelPipelines should be marked as ChannelHandler.Sharable.

6. Additional resources worth reading
Please refer to the ChannelHandler, and ChannelPipeline to find out more about inbound and outbound operations, what fundamental differences they have, how they flow in a pipeline, and how to handle the operation in your application.
```


DefaultChannelPipeline
```text
DefaultChannelPipeline中每个ChannelHandler都有自己的ChannelHandlerContext,ChannelHandlerContext在ChannelPipeline以单向链表存在,其中链表的头尾节点都是dummy节点.
dummy节点对应是HeadContext/TailContext.
当向DefaultChannelPipeline中插入ChannelHandler时,就是在header/tail节点中间插入节点.

io.netty.channel.DefaultChannelPipeline.addLast0
private void addLast0(AbstractChannelHandlerContext newCtx) {
    AbstractChannelHandlerContext prev = tail.prev;
    newCtx.prev = prev;
    newCtx.next = tail;
    prev.next = newCtx;
    tail.prev = newCtx;
}

private void addFirst0(AbstractChannelHandlerContext newCtx) {
    AbstractChannelHandlerContext nextCtx = head.next;
    newCtx.prev = head;
    newCtx.next = nextCtx;
    head.next = newCtx;
    nextCtx.prev = newCtx;
}
```

```text
为新增的ChannelHandler创建相应的ChannelHandlerContext,并将ChannelHandlerContext插入到DefaultChannelPipeline中;
然后调用回调方法ChannelHandler.handlerAdded,表示ChannelHandler was added to the actual context and it's ready to handle.

private ChannelPipeline internalAdd(EventExecutorGroup group, String name, ChannelHandler handler, String baseName, AddStrategy addStrategy) {
    final AbstractChannelHandlerContext newCtx;
    synchronized (this) {
        checkMultiplicity(handler);
        name = filterName(name, handler);
        // 实例化一个对象(new DefaultChannelHandlerContext),并插入到链表中
        newCtx = newContext(group, name, handler);
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
        if (!registered) {
            newCtx.setAddPending();
            // 间接调用callHandlerAdded
            callHandlerCallbackLater(newCtx, true);
            return this;
        }

        EventExecutor executor = newCtx.executor();
        if (!executor.inEventLoop()) {
            // 间接调用callHandlerAdded
            callHandlerAddedInEventLoop(newCtx, executor);
            return this;
        }
    }
    
    // 间接调用callHandlerAdded
    callHandlerAdded0(newCtx);
    return this;
}


final void callHandlerAdded() throws Exception {
    // We must call setAddComplete before calling handlerAdded. Otherwise if the handlerAdded method generates
    // any pipeline events ctx.handler() will miss them because the state will not allow it.
    if (setAddComplete()) {
        handler().handlerAdded(this);
    }
}
```

## ChannelPipeline
```text
A list of ChannelHandlers which handles or intercepts inbound events and outbound operations of a Channel. 
ChannelPipeline implements an advanced form of the Intercepting Filter  pattern to give a user full control over how an event is handled and how the ChannelHandlers in a pipeline interact with each other.

Creation of a pipeline
Each channel has its own pipeline and it is created automatically when a new channel is created.

How an event flows in a pipeline
An I/O event is handled by either a ChannelInboundHandler or a ChannelOutboundHandler 
and be forwarded to its closest handler by calling the event propagation methods defined in ChannelHandlerContext, 
such as ChannelHandlerContext.fireChannelRead(Object) and ChannelHandlerContext.write(Object).
```