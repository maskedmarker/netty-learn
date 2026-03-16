package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;


public class NettyTcpServerTest {

    private static final int PORT = 8080;

    /**
     *
     */
    @Test
    public void test() throws Exception {
        // 创建两个线程组：boss用于接受连接，worker用于处理I/O
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)                    // 为server-socket配置: 全连接队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true)             // 为接收到的client-socket配置: tcp保活
                    .childOption(ChannelOption.SO_TIMEOUT, 60 * 1000)          // 为接收到的client-socket配置: 读超时一分钟
                    .handler(new LoggingHandler(LogLevel.INFO))                      // 为server-socket-channel准备的handler
                    .childHandler(new ChannelInitializer<SocketChannel>() {          // 为接收到的client-socket准备handler
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new TcpServerHandler());
                        }
                    });

            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.println("TCP服务器启动,端口：" + PORT);

            // 等待服务器关闭
            future.channel().closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    class TcpServerHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
            System.out.println("收到消息: " + msg.toString(StandardCharsets.UTF_8));

            ctx.writeAndFlush("服务器已收到: " + msg + "\n");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            cause.printStackTrace();
            ctx.close();
        }
    }
}
