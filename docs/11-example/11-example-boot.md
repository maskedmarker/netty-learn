# explain example


```text
public void test() throws Exception {
    URI uri = new URI("http://example.com");
    String host = uri.getHost();
    int port = uri.getPort() == -1 ? 80 : uri.getPort();

    EventLoopGroup group = new NioEventLoopGroup();                                            // 创建处理channel事件的线程池(会同步创建nio的Selector)
    try {
        Bootstrap b = new Bootstrap();
        b.group(group)                                                                         // 设置线程池来处理channel的各种event
                .channel(NioSocketChannel.class)                                               // 指定pipeline的outbound流向的bottom的channel的类型(不止支持bio/nio的普通socket,还支持domain-socket,以及其他类似于i/o接口)
                .handler(new ChannelInitializer<SocketChannel>() {                             // Bootstrap只支持一个ChannelHandler(主要用于初始化channel的pipeline),channel的pipeline是支持多个channelHandler.这个特殊的ChannelHandler主要用于为channel的pipeline动态添加channelHandler
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpContentDecompressor());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024));
                        ch.pipeline().addLast(new SimpleHttpClientHandler());
                    }
                });

        Channel channel = b.connect(host, port).sync().channel();

        // 构造 GET 请求
        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1, HttpMethod.GET, uri.getRawPath().isEmpty() ? "/" : uri.getRawPath(),
                Unpooled.EMPTY_BUFFER);

        request.headers().set(HttpHeaderNames.HOST, host);
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP);

        // 发送请求
        channel.writeAndFlush(request);

        // 等待关闭
        channel.closeFuture().sync();
    } finally {
        group.shutdownGracefully();
    }
}
```






























