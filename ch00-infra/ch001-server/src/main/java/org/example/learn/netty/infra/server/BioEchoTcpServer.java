package org.example.learn.netty.infra.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class BioEchoTcpServer {

    private final int port;

    private AtomicBoolean running = new AtomicBoolean(false);

    ServerSocket serverSocket;

    private static final ExecutorService threadPool = Executors.newFixedThreadPool(10); // 固定线程池

    public BioEchoTcpServer(int port) {
        this.port = port;
    }

    public void start() {
        // 单独启动一个io线程
        Thread ioThread = new Thread(() -> {
            try {
                this.initSocket();
                this.handleTcpRequest();
            } catch (Exception e) {
                System.out.println("NioTcpServer发生异常");
                e.printStackTrace();
            }
        });
        ioThread.start();
        running.set(true);
    }

    public void stop() {
        running.set(false);
    }

    public boolean isRunning() {
        return running.get();
    }

    private void initSocket() throws IOException {
        serverSocket = new ServerSocket(this.port);
        serverSocket.setReuseAddress(true);
        System.out.printf("Tcp Server started at %s\n", serverSocket.getLocalPort());
    }

    private void handleTcpRequest() throws Exception {
        while (running.get()) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Accepted connection from: " + clientSocket.getRemoteSocketAddress());

            threadPool.submit(new HttpHandler(clientSocket));
        }
    }


    static class HttpHandler implements Runnable {
        private final Socket clientSocket;

        public HttpHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try (InputStream in = clientSocket.getInputStream();
                 OutputStream out = clientSocket.getOutputStream()) {

                // 读取客户端数据并原样返回
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();

                    // 打印接收到的数据（可选）
                    System.out.println("接收到请求:");
                    String request = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    System.out.println(request);
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                try {
                    clientSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
