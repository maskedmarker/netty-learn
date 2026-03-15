package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

public class NettyHttpClientTest {

    // 防止接收的http响应过大,需要设置一个接收最大值(只用考虑content的大小,header的大小是有限制的)
    private static final int RESPONSE_CONTENT_MAX_SIZE = 1024 * 1024;

    @Test
    public void test() throws Exception {
        URI uri = new URI("http://example.com");
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 80 : uri.getPort();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpContentDecompressor());
                            // A ChannelHandler that aggregates an HttpMessage and its following HttpContents into a single FullHttpRequest or FullHttpResponse with no following HttpContents.(分块传输时,一个响应会有多个content块)
                            ch.pipeline().addLast(new HttpObjectAggregator(RESPONSE_CONTENT_MAX_SIZE));
                            ch.pipeline().addLast(new SimpleHttpClientHandler());
                        }
                    });

            Channel channel = b.connect(host, port).sync().channel();

            // 构造 GET 请求
            String content = "hello world";
            byte[] contentBytes = content.getBytes();

            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, (uri.getRawPath().isEmpty() ? "/" : uri.getRawPath()), Unpooled.wrappedBuffer(contentBytes));
            request.headers().set(HttpHeaderNames.HOST, host)
                    .set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE)                  // 让服务端主动关闭连接
                    .set(HttpHeaderNames.ACCEPT_ENCODING, HttpHeaderValues.GZIP)
                    .set(HttpHeaderNames.CONTENT_LENGTH, contentBytes.length);


            // 发送请求
            channel.writeAndFlush(request);

            // 等待关闭
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    static class SimpleHttpClientHandler extends SimpleChannelInboundHandler<FullHttpResponse> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse response) throws Exception {
            System.out.println("=== HTTP Response ===");
            System.out.println("Status: " + response.status());
            System.out.println("Headers: ");
            response.headers().forEach(h -> System.out.println(h.getKey() + ": " + h.getValue()));

            System.out.println("content.readableBytes() = " + response.content().readableBytes());
            String contentEncoding = response.headers().get(HttpHeaderNames.CONTENT_ENCODING);
            boolean isGzip = "gzip".equalsIgnoreCase(contentEncoding);
            System.out.println("\nBody:");
            if (isGzip) {
                ByteArrayInputStream bais = new ByteArrayInputStream(response.content().array());
                GZIPInputStream gzipStream = new GZIPInputStream(bais);
                BufferedReader reader = new BufferedReader(new InputStreamReader(gzipStream, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } else {
                System.out.println(response.content().toString(StandardCharsets.UTF_8));
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.err.println("❌ Exception: " + cause.getMessage());
            cause.printStackTrace();
            ctx.close();
        }
    }
}
