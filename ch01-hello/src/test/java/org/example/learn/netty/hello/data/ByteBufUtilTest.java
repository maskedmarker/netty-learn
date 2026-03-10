package org.example.learn.netty.hello.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.example.learn.netty.hello.util.ByteBufUtils;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * ByteBufUtil是netty自带的工具类
 */
public class ByteBufUtilTest {

    /**
     *
     */
    @Test
    public void test01() {
        ByteBuf buf1 = Unpooled.copiedBuffer("hello world".getBytes(StandardCharsets.UTF_8));

        ByteBuf buf2 = Unpooled.buffer();
        ByteBufUtil.writeUtf8(buf2, "hello world");

        Assert.assertEquals(buf1, buf2);
    }
}
