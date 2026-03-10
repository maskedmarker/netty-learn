package org.example.learn.netty.hello.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * ByteBuf转换
 */
public class ByteBuf3Test {

    /**
     * 字节数组 -> 字符串
     */
    @Test
    public void test01() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);

        String string = byteBuf.toString(StandardCharsets.UTF_8);
        System.out.println("string = " + string);
    }


}
