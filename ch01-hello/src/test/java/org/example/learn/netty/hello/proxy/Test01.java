package org.example.learn.netty.hello.proxy;

import org.example.learn.netty.hello.proxy.http.HttpProxyServer;
import org.junit.Test;

public class Test01 {

    @Test
    public void test() throws Exception {
        HttpProxyServer httpProxyServer = new HttpProxyServer();
        httpProxyServer.start();
        System.in.read();
    }
}
