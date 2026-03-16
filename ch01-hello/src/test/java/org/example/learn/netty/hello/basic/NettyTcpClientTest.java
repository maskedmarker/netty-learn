package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
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
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;


public class NettyTcpClientTest {

    /**
     * 也可以通过编码方式设置日志配置
     */
    @Before
    public void setup() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (Handler h : root.getHandlers()) {
            h.setLevel(Level.FINE);
        }
    }

    /**
     * 本地debug可以启动BioEchoTcpServer的test方法
     */
    @Test(timeout = 10 * 1000)
    public void test() throws Exception {
        String host = "localhost";
        int port = 8080;

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.DEBUG))
                                    .addLast(new ClientHandler());      // 处理接收的数据
                        }
                    });
            // 建立tcp连接
            Channel channel = clientBootstrap.connect(host, port).sync().channel();

            // 构造请求
            ByteBuf request = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));
            // 发送请求
            ChannelFuture writeFuture = channel.writeAndFlush(request);
            // 发送完毕后,主动关闭连接
            // writeFuture.addListener(ChannelFutureListener.CLOSE);
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }

    private static class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

        @Override
        public void handlerAdded(ChannelHandlerContext ctx) throws Exception {     // handlerAdded先于channelActive,handler在added之前是无法接收消息的(通常也要求channel已经被registered了)
            System.out.println("handler被加入ChannelHandlerContext");
        }

        @Override
        public void channelRegistered(ChannelHandlerContext ctx) throws Exception {  // handlerAdded和channelRegistered的没有明显的先后顺序,尽量不要依赖事件的先后顺序
            System.out.println("通道已被注册");
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            System.out.println("通道已激活");
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            System.out.println("通道已失去激活");
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            System.out.println("收到服务器数据: " + ByteBufUtil.hexDump(msg));
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.out.println("发生异常,将关闭连接");
            cause.printStackTrace();
            ctx.close();
        }
    }
}
