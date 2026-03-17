# ServerBootstrap

server端的启动类
(为了简化描述,这里只讨论socket类型的Channel)

使用样例
```text
public static void main(String[] args) throws Exception {
    EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    EventLoopGroup workerGroup = new NioEventLoopGroup();
    try {
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .handler(new LoggingHandler(LogLevel.INFO))
                .childHandler(new HAProxyServerInitializer());
        b.bind(PORT).sync().channel().closeFuture().sync();
    } finally {
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
    }
}
```



## bind触发ServerBootstrap启动

```text
private ChannelFuture doBind(final SocketAddress localAddress) {
    final ChannelFuture regFuture = initAndRegister();
    final Channel channel = regFuture.channel();
    if (regFuture.cause() != null) {
        return regFuture;
    }

    if (regFuture.isDone()) {
        // At this point we know that the registration was complete and successful.
        ChannelPromise promise = channel.newPromise();
        doBind0(regFuture, channel, localAddress, promise);
        return promise;
    } else {
        // Registration future is almost always fulfilled already, but just in case it's not.
        final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
        regFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                Throwable cause = future.cause();
                if (cause != null) {
                    // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                    // IllegalStateException once we try to access the EventLoop of the Channel.
                    promise.setFailure(cause);
                } else {
                    // Registration was successful, so set the correct executor to use.
                    // See https://github.com/netty/netty/issues/2586
                    promise.registered();

                    doBind0(regFuture, channel, localAddress, promise);
                }
            }
        });
        return promise;
    }
}
```

```text
final ChannelFuture initAndRegister() {
    Channel channel = null;
    try {
        channel = channelFactory.newChannel();
        init(channel);
    } catch (Throwable t) {
        if (channel != null) {
            // channel can be null if newChannel crashed (eg SocketException("too many open files"))
            channel.unsafe().closeForcibly();
            // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
            return new DefaultChannelPromise(channel, GlobalEventExecutor.INSTANCE).setFailure(t);
        }
        // as the Channel is not registered yet we need to force the usage of the GlobalEventExecutor
        return new DefaultChannelPromise(new FailedChannel(), GlobalEventExecutor.INSTANCE).setFailure(t);
    }

    ChannelFuture regFuture = config().group().register(channel);
    if (regFuture.cause() != null) {
        if (channel.isRegistered()) {
            channel.close();
        } else {
            channel.unsafe().closeForcibly();
        }
    }

    return regFuture;
}
```

## init
ServerBootstrap与Bootstrap的init实现不同

```text
void init(Channel channel) {
    setChannelOptions(channel, newOptionsArray(), logger);
    setAttributes(channel, newAttributesArray());

    ChannelPipeline p = channel.pipeline();

    final EventLoopGroup currentChildGroup = childGroup;
    final ChannelHandler currentChildHandler = childHandler;
    final Entry<ChannelOption<?>, Object>[] currentChildOptions = newOptionsArray(childOptions);
    final Entry<AttributeKey<?>, Object>[] currentChildAttrs = newAttributesArray(childAttrs);
    final Collection<ChannelInitializerExtension> extensions = getInitializerExtensions();

    p.addLast(new ChannelInitializer<Channel>() {
        @Override
        public void initChannel(final Channel ch) {
            final ChannelPipeline pipeline = ch.pipeline();
            ChannelHandler handler = config.handler();
            if (handler != null) {
                pipeline.addLast(handler);                                                                                              // 为server-socket配置handler
            }

            ch.eventLoop().execute(new Runnable() {
                @Override
                public void run() {
                    pipeline.addLast(new ServerBootstrapAcceptor(
                            ch, currentChildGroup, currentChildHandler, currentChildOptions, currentChildAttrs, extensions));            // 为server-socket配置特殊的handler,专门用来处理新连接的accept事件💯
                }
            });
        }
    });
    
    // ...
}
```

## ServerBootstrapAcceptor

server-socket将accept的新连接作为read到的msg,触发fireChannelRead.
ServerBootstrapAcceptor接收到channelRead事件后,无需该事件进一步传播.

```text
private static class ServerBootstrapAcceptor extends ChannelInboundHandlerAdapter {
        
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        final Channel child = (Channel) msg;                                                    // msg即server-socket accept到的新连接,可以大胆cast到Channel

        child.pipeline().addLast(childHandler);                                                 // 将ServerBootstrap中配置的childHandler添加到新连接的pipeline中
        setChannelOptions(child, childOptions, logger);                                         // 将ServerBootstrap中配置的childOptions/childAttrs设置到新client-socket中
        setAttributes(child, childAttrs);

        // ...
            
        try {
            // 将新连接注册childGroup的一个NioEventLoop上,然后注册到NioEventLoop管理的selector上💯💯💯(默认情况下新连接的所有事件将由注册的NioEventLoop负责)
            // 而且netty做了代码保护,不允许一个channel被注册到多个EventLoop上💯💯💯💯 (参见io.netty.channel.AbstractChannel.AbstractUnsafe.register)
            // 一个channel只有一个eventLoop操作,避免并发问题,这是Netty高性能的重要原因
            childGroup.register(child).addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (!future.isSuccess()) {
                        forceClose(child, future.cause());                                        // 如果注册失败则关闭连接
                    }
                }
            });
        } catch (Throwable t) {
            forceClose(child, t);
        }
    }
}
```
























