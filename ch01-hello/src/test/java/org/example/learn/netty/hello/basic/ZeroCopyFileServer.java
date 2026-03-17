package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import org.junit.Test;

import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ssl是流式加密技术,每次需要加密的数据是有大小限制的.
 */
public class ZeroCopyFileServer {

    public static final int PORT = 8080;

    @Test
    public void test() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(1);  // 当不指定线程数时,由启动参数io.netty.eventLoopThreads或平台cpu线程*2决定

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new FileServerHandler());
                        }
                    });

            ChannelFuture future = bootstrap.bind(PORT).sync();
            System.out.printf("zero-copy-file-server started at %d\n", PORT);

            future.channel().closeFuture().sync();
            System.out.printf("zero-copy-file-server closed at %d\n", PORT);
        } finally {
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }

    private static class FileServerHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            String cwd = System.getProperty("user.dir");
            String fileName = "test.data";
            Path filePath = Paths.get(cwd, "src/test/resources", fileName);
            RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), "r");
            FileChannel fileChannel = raf.getChannel();
            long length = fileChannel.size();
            System.out.println("sending-file size: " + length);

            ChannelFuture opFuture;
            if (ctx.pipeline().get(SslHandler.class) == null) {
                DefaultFileRegion region = new DefaultFileRegion(fileChannel, 0, length);
                opFuture = ctx.writeAndFlush(region);
            } else {
                opFuture = ctx.writeAndFlush(new ChunkedFile(raf));
            }

            opFuture.addListener(f -> {
                        System.out.println("Send complete");
                        raf.close();
                    })
                    .addListener(ChannelFutureListener.CLOSE);
        }
    }
}
