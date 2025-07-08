package org.example.learn.netty.hello.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class NioHttpClientTest {

    public static final int PORT = 8080;
    public static final String HOST = "localhost";

    @Test
    public void test() throws IOException {
        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.connect(new InetSocketAddress(HOST, PORT));

        String request = "GET / HTTP/1.1\r\n" +
                "Host: " + HOST + "\r\n" +
                "Connection: close\r\n\r\n";

        socketChannel.write(ByteBuffer.wrap(request.getBytes()));
        // 没有设置成non-blocking模式
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        while (socketChannel.read(buffer) > 0) {
            buffer.flip();
            System.out.print(new String(buffer.array(), 0, buffer.limit()));
            buffer.clear();
        }

        socketChannel.close();
    }
}
