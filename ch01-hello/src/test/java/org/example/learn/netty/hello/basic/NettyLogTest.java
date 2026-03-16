package org.example.learn.netty.hello.basic;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 在只使用 netty-all且没有引入任何日志框架（log4j / logback / slf4j）的情况下,Netty 会自动使用 JDK 自带日志 java.util.logging (JUL)
 * 因此你只需要配置JUL的logging.properties
 * java -Djava.util.logging.config.file=logging.properties  yourMainClass
 *
 * 注意:netty并没有提供logging的API,但是netty支持常见的facade接口框架
 */
public class NettyLogTest {


    /**
     * 也可以通过编码方式设置日志配置
     */
    @Before
    public void setup() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.FINEST);
        for (Handler h : root.getHandlers()) {
            h.setLevel(Level.FINE);
        }
    }

    @Test
    public void test() throws Exception {
        String host = "localhost";
        int port = 8080;

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap clientBootstrap = new Bootstrap();
            clientBootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.TRACE));

            // 等待建立tcp连接完成后主动关闭连接
            Channel channel = clientBootstrap.connect(host, port).sync().channel();
            channel.close().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
