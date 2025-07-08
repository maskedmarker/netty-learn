package org.example.learn.netty.hello.nio;

import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

/**
 * 深刻理解原生nio代码,才能更快理解netty的代码设计
 */
public class NioHttpServerTest {

    public static final int PORT = 8080;

    @Test
    public void test0() throws Exception {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        // 先完成bind
        serverChannel.bind(new InetSocketAddress(PORT));
        serverChannel.configureBlocking(false);

        Selector selector = Selector.open();
        // 先完成bind再向selector注册
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("HTTP Server started at http://localhost:" + PORT);

        while (true) {
            selector.select(); // 阻塞直到有事件发生
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (key.isAcceptable()) {
                    // netty将serverSocketChannel accept到的socketChannel称呼为childChannel
                    SocketChannel childChannel = serverChannel.accept();
                    childChannel.configureBlocking(false);
                    childChannel.register(selector, SelectionKey.OP_READ);
                } else if (key.isReadable()) {
                    handleRequest(key);
                }
            }
        }
    }

    private void handleRequest(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        int read = client.read(buffer);
        if (read == -1) {
            client.close();
            return;
        }

        buffer.flip();
        String request = new String(buffer.array(), 0, buffer.limit());
        System.out.println("接收到请求:\n" + request);

        // 简单 HTTP 响应
        String responseBody = "Hello from NIO HTTP Server!";
        String httpResponse = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain; charset=utf-8\r\n" +
                "Content-Length: " + responseBody.getBytes().length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                responseBody;

        client.write(ByteBuffer.wrap(httpResponse.getBytes()));
        client.close();
    }
}
