package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;


public class TcpClientTest {


    /**
     * 本地debug可以启动BioEchoTcpServer的test方法
     */
    @Test
    public void test() throws Exception {
        String host = "localhost";
        int port = 8080;

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new ByteEncoder());    // 将用户需要发送的数据类型转换为可以发送的数据类型(ByteBuf)
                            ch.pipeline().addLast(new ByteDecoder());    // 将接收的数据类型(ByteBuf)转换为后续用户需要的数据类型
                            ch.pipeline().addLast(new ClientHandler());  // 处理接收的数据
                        }
                    });
            // 建立tcp连接
            Channel channel = b.connect(host, port).sync().channel();

            // 构造请求
            ByteBuf request = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
            // 发送请求
            ChannelFuture channelFuture = channel.writeAndFlush(request);
            // 发送完毕后,关闭连接
            channelFuture.sync();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    // MessageToByteEncoder是ChannelOutboundHandler
    private static class ByteEncoder extends MessageToByteEncoder<ByteBuf> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
            out.writeBytes(msg);
        }
    }

    // ByteToMessageDecoder是ChannelInboundHandler
    private static class ByteDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            if (in.readableBytes() > 0) {
                // 读取所有可用字节
                byte[] bytes = new byte[in.readableBytes()];
                in.readBytes(bytes);
                out.add(bytes);
            }
        }
    }

    // 客户端处理器
    private static class ClientHandler extends SimpleChannelInboundHandler<byte[]> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
            System.out.print("收到服务器数据: ");
            for (byte b : msg) {
                System.out.printf("0x%02X ", b & 0xFF);
            }
            System.out.println();
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("通道已激活,可以开始发送数据");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
