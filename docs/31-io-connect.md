# example-explain-i/o

## connect

样例代码
```text
Channel channel = bootstrap.connect(host, port).sync().channel();
```

实际调用
```text
io.netty.bootstrap.Bootstrap.connect(java.lang.String, int)
    io.netty.bootstrap.Bootstrap.connect(java.net.SocketAddress)
        io.netty.bootstrap.Bootstrap.doResolveAndConnect
            io.netty.bootstrap.Bootstrap.doResolveAndConnect0
                io.netty.channel.AbstractChannel.connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)                        (Channel.connect发起一个connect请求)
                    io.netty.channel.DefaultChannelPipeline.connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)             (out-bound操作请求需要经过pipeline的责任链拦截,所以out-bound操作需要从TailContext开始)
                        io.netty.channel.AbstractChannelHandlerContext.connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)  
                            io.netty.channel.AbstractChannelHandlerContext.invokeConnect                                                                          ()
                                io.netty.channel.DefaultChannelPipeline.HeadContext.connect                                                                       (netty框架在HeadContext中实现了out-bound connect I/O操作)
                                    io.netty.channel.nio.AbstractNioChannel.AbstractNioUnsafe.connect                                                             (底层调用java-nio的操作都被封装到了NioUnsafe类中)
                                        io.netty.channel.socket.nio.NioSocketChannel.doConnect
                                            io.netty.channel.socket.nio.NioSocketChannel.doBind (connect前client-socket需要先绑定本地端口)
                                                io.netty.channel.socket.nio.NioSocketChannel.doBind0



io.netty.bootstrap.Bootstrap.connect(java.lang.String, int)
    public ChannelFuture connect(String inetHost, int inetPort) {
        return connect(InetSocketAddress.createUnresolved(inetHost, inetPort));
    }

io.netty.bootstrap.Bootstrap.connect(java.net.SocketAddress)
    public ChannelFuture connect(SocketAddress remoteAddress) {
        ObjectUtil.checkNotNull(remoteAddress, "remoteAddress");
        validate();
        return doResolveAndConnect(remoteAddress, config.localAddress());
    }

io.netty.bootstrap.Bootstrap.doResolveAndConnect
    private ChannelFuture doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
        final ChannelFuture regFuture = initAndRegister();
        final Channel channel = regFuture.channel();

        if (regFuture.isDone()) {
            if (!regFuture.isSuccess()) {
                return regFuture;
            }
            return doResolveAndConnect0(channel, remoteAddress, localAddress, channel.newPromise());
        } else {
            //...
            return promise;
        }
    }


io.netty.bootstrap.Bootstrap.doResolveAndConnect0
    private ChannelFuture doResolveAndConnect0(final Channel channel, SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise promise) {
        try {
            if (disableResolver) {
                doConnect(remoteAddress, localAddress, promise);
                return promise;
            }
            // ...
        } catch (Throwable cause) {
            promise.tryFailure(cause);
        }
        return promise;
    }

io.netty.bootstrap.Bootstrap.doConnect
    private static void doConnect(final SocketAddress remoteAddress, final SocketAddress localAddress, final ChannelPromise connectPromise) {

        // This method is invoked before channelRegistered() is triggered.  Give user handlers a chance to set up the pipeline in its channelRegistered() implementation.
        final Channel channel = connectPromise.channel();
        channel.eventLoop().execute(new Runnable() {
            @Override
            public void run() {
                if (localAddress == null) {
                    channel.connect(remoteAddress, connectPromise);
                } else {
                    channel.connect(remoteAddress, localAddress, connectPromise);
                }
                connectPromise.addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            }
        });
    }


io.netty.channel.AbstractChannel.connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)
    public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return pipeline.connect(remoteAddress, localAddress, promise);
    }

io.netty.channel.DefaultChannelPipeline.connect(java.net.SocketAddress, java.net.SocketAddress, io.netty.channel.ChannelPromise)    
    public final ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        return tail.connect(remoteAddress, localAddress, promise);  // 💯💯💯 out-bound是从tail->head的责任链处理,这样所有handler都能处理connect请求
    } 
    
io.netty.channel.AbstractChannelHandlerContext.invokeConnect
    private void invokeConnect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
        if (invokeHandler()) {
            try {
                // Duplex handlers implements both out/in interfaces causing a scalability issue
                final ChannelHandler handler = handler();
                final DefaultChannelPipeline.HeadContext headContext = pipeline.head;
                if (handler == headContext) {
                    headContext.connect(this, remoteAddress, localAddress, promise);
                } else if (handler instanceof ChannelDuplexHandler) {
                    ((ChannelDuplexHandler) handler).connect(this, remoteAddress, localAddress, promise);
                } else if (handler instanceof ChannelOutboundHandlerAdapter) {
                    ((ChannelOutboundHandlerAdapter) handler).connect(this, remoteAddress, localAddress, promise);
                } else {
                    ((ChannelOutboundHandler) handler).connect(this, remoteAddress, localAddress, promise);
                }
            } catch (Throwable t) {
                notifyOutboundHandlerException(t, promise);
            }
        } else {
            connect(remoteAddress, localAddress, promise);
        }
    }  
    

io.netty.channel.DefaultChannelPipeline.HeadContext.connect    
        public void connect(
                ChannelHandlerContext ctx,
                SocketAddress remoteAddress, SocketAddress localAddress,
                ChannelPromise promise) {
            unsafe.connect(remoteAddress, localAddress, promise);
        }   
        
io.netty.channel.nio.AbstractNioChannel.AbstractNioUnsafe.connect

io.netty.channel.socket.nio.NioSocketChannel.doBind
io.netty.channel.socket.nio.NioSocketChannel.doBind0
    private void doBind0(SocketAddress localAddress) throws Exception {
        if (PlatformDependent.javaVersion() >= 7) {
            SocketUtils.bind(javaChannel(), localAddress);
        } else {
            SocketUtils.bind(javaChannel().socket(), localAddress);
        }
    }              
```
