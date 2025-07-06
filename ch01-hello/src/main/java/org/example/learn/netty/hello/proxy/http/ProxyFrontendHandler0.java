package org.example.learn.netty.hello.proxy.http;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpScheme;

import java.net.URI;

public class ProxyFrontendHandler0 extends SimpleChannelInboundHandler<FullHttpRequest> {

    public static final int MAX_CONTENT_LENGTH = 65536;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        String uri = request.uri(); // 完整 URL，如 http://example.com/index.html
        URI parsedUri = URI.create(uri);

        String host = parsedUri.getHost();
        String scheme = parsedUri.getScheme();
        int port = parsedUri.getPort();
        if (port == -1) {
            port = HttpScheme.HTTP.name().contentEqualsIgnoreCase(scheme) ? HttpScheme.HTTP.port() : HttpScheme.HTTPS.port();
        }

        // 建立到目标服务器的连接
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        ch.pipeline().addLast(new ProxyBackendHandler(ctx.channel()));
                    }
                });

        bootstrap.connect(host, port)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        // 转发请求
                        future.channel().writeAndFlush(request.retain());
                    } else {
                        ctx.close();
                    }
                });
    }
}
