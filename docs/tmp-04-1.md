# ChannelInitializer

ChannelInitializer按照用途分为2大类,一类用于ServerSocketChannel的pipeline,一类用于SocketChannel的pipeline.


## ChannelInitializer的执行关键节点

```text
io.netty.bootstrap.ServerBootstrap.init

    void init(Channel channel) {
        setChannelOptions(channel, newOptionsArray(), logger);
        setAttributes(channel, newAttributesArray());

        ChannelPipeline p = channel.pipeline();

        final EventLoopGroup currentChildGroup = childGroup;
        // 用户自定义的ChannelInitializer或者普通ChannelHandler
        final ChannelHandler currentChildHandler = childHandler;
        final Entry<ChannelOption<?>, Object>[] currentChildOptions = newOptionsArray(childOptions);
        final Entry<AttributeKey<?>, Object>[] currentChildAttrs = newAttributesArray(childAttrs);
        final Collection<ChannelInitializerExtension> extensions = getInitializerExtensions();
        
        // 框架为serverSocketChannel的pipeline新增框架层的ChannelInitializer
        p.addLast(new ChannelInitializer<Channel>() {
            @Override
            public void initChannel(final Channel ch) {
                final ChannelPipeline pipeline = ch.pipeline();
                // ServerBootstrapConfig也支持为serverSocketChannel新增自定义节点
                ChannelHandler handler = config.handler();
                if (handler != null) {
                    pipeline.addLast(handler);
                }

                ch.eventLoop().execute(new Runnable() {
                    @Override
                    public void run() {
                        // 为为serverSocketChannel的pipeline新增必须节点ServerBootstrapAcceptor
                        pipeline.addLast(new ServerBootstrapAcceptor(ch, currentChildGroup, /*注意: 这是用户自定义的ChannelInitializer或者普通ChannelHandler*/currentChildHandler, currentChildOptions, currentChildAttrs, extensions));
                    }
                });
            }
        });
        // ...
    }
```

```text
    private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {

        private final EventLoopGroup childGroup;
        // 这是用户自定义的用于child-channel的ChannelInitializer或者普通ChannelHandler
        private final ChannelHandler childHandler;
        private final Entry<ChannelOption<?>, Object>[] childOptions;
        private final Entry<AttributeKey<?>, Object>[] childAttrs;
        private final Runnable enableAutoReadTask;
        private final Collection<ChannelInitializerExtension> extensions;

        ServerBootstrapAcceptor(final Channel channel, EventLoopGroup childGroup, ChannelHandler childHandler, Entry<ChannelOption<?>, Object>[] childOptions, Entry<AttributeKey<?>, Object>[] childAttrs, Collection<ChannelInitializerExtension> extensions) {
            this.childGroup = childGroup;
            this.childHandler = childHandler;
            this.childOptions = childOptions;
            this.childAttrs = childAttrs;
            this.extensions = extensions;
            // ...
        }
        
        // 对于serverSocketChannel,当tcp建立连接后,会触发channelRead方法回调(accept也被当作read事件)
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            // child即serverSocket accept返回的非serverSocket类型的socketChannel
            final Channel child = (Channel) msg;
            // 当tcp建立连接后,在serverSocketChannel的pipeline中,动态地将用户自定义的用于child-channel的ChannelInitializer或者普通ChannelHandler追加child-channel(即accept接收到的socketChannel)的pipeline中
            // 在child-channel的pipeline中新增ChannelInitializer,会触发handlerAdded,进而触发ChannelInitializer的初始化过程
            child.pipeline().addLast(childHandler);

            setChannelOptions(child, childOptions, logger);
            setAttributes(child, childAttrs);

            if (!extensions.isEmpty()) {
                for (ChannelInitializerExtension extension : extensions) {
                    try {
                        extension.postInitializeServerChildChannel(child);
                    } catch (Exception e) {
                        logger.warn("Exception thrown from postInitializeServerChildChannel", e);
                    }
                }
            }

            try {
                // 当tcp建立连接后,需要在worker eventLoop中注册接收到的socketChannel,注册socketChannel又会触发socketChannel的pipeline的fireChannelRegistered
                childGroup.register(child).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            forceClose(child, future.cause());
                        }
                    }
                });
            } catch (Throwable t) {
                forceClose(child, t);
            }
        }
}        
```


### ChannelInitializer
```text
ChannelInitializer被添加到pipeline,并不是为了使用使用ChannelInitializer本身,而是让ChannelInitializer做一些其他的初始化工作.
当ChannelInitializer做完这些初始化工作后,也就意味着pipeline不再需要该ChannelInitializer(因为初始化工作仅仅执行一次),需要将ChannelInitializer从pipeline移除

public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
    if (ctx.channel().isRegistered()) {
        // This should always be true with our current DefaultChannelPipeline implementation.
        // The good thing about calling initChannel(...) in handlerAdded(...) is that there will be no ordering surprises if a ChannelInitializer will add another ChannelInitializer. This is as all handlers will be added in the expected order.
        if (initChannel(ctx)) {
            // We are done with init the Channel, removing the initializer now.
            removeState(ctx);
        }
    }
}
```