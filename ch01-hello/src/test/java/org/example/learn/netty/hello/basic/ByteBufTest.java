package org.example.learn.netty.hello.basic;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

public class ByteBufTest {

    @Test
    public void test() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("Hello".getBytes(StandardCharsets.UTF_8));

        // 读取数据
        byte[] dst = new byte[buf.readableBytes()];
        buf.readBytes(dst);
        System.out.println(new String(dst, StandardCharsets.UTF_8));

        // 释放内存（如果是 Direct Buffer）
        if (buf instanceof UnpooledDirectByteBuf) {
            buf.release();
        }
    }

    @Test
    public void test1() throws Exception {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes("中文".getBytes(StandardCharsets.UTF_8));

        // 读取数据
        byte[] dst = new byte[buf.readableBytes()];
        buf.readBytes(dst);
        System.out.println(new String(dst, StandardCharsets.UTF_8));

        // 释放内存（如果是 Direct Buffer）
        if (buf instanceof UnpooledDirectByteBuf) {
            buf.release();
        }
    }
}
