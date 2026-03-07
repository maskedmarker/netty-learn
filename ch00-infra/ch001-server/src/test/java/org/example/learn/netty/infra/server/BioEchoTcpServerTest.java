package org.example.learn.netty.infra.server;


import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class BioEchoTcpServerTest {

    public static final int PORT = 8080;

    @Test
    public void test0() throws InterruptedException {
        BioEchoTcpServer bioEchoTcpServer = new BioEchoTcpServer(PORT);
        bioEchoTcpServer.start();

        while (bioEchoTcpServer.isRunning()) {
            TimeUnit.SECONDS.sleep(1);
        }
    }
}