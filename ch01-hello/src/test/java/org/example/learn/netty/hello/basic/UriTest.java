package org.example.learn.netty.hello.basic;

import org.junit.Test;

import java.net.URI;

public class UriTest {

    @Test
    public void test0() {
        String uri = "http://example.com/index.html";
        URI parsedUri = URI.create(uri);
        String host = parsedUri.getHost();
        String scheme = parsedUri.getScheme();
        int port = parsedUri.getPort();

        System.out.println("scheme = " + scheme);
        System.out.println("host = " + host);
        System.out.println("port = " + port);
    }
}
