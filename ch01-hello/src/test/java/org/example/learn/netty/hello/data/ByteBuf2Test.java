package org.example.learn.netty.hello.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.example.learn.netty.hello.util.ByteBufUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 *
 */
public class ByteBuf2Test {

    /**
     * copiedBuffer的底层数组与原数组无联动
     * Creates a new big-endian buffer whose content is a copy of the specified array
     */
    @Test
    public void test01() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = Unpooled.copiedBuffer(bytes);
        ByteBufUtils.log("", byteBuf);
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
        bytes[10] = 0x6c;
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
    }


    /**
     * wrappedBuffer的底层数组就是原数组,所以改动原数组时,会发生联动
     * Creates a new big-endian buffer which wraps the specified array. A modification on the specified array's content will be visible to the returned buffer.
     */
    @Test
    public void test02() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        ByteBufUtils.log("", byteBuf);
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
        bytes[10] = 0x6c;
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
    }

    /**
     * copy底层数组与原数组无联动
     * Returns a copy of this buffer's readable bytes. Modifying the content of the returned buffer or this buffer does not affect each other at all.
     */
    @Test
    public void test03() {
        byte[] bytes = "hello world".getBytes(StandardCharsets.UTF_8);
        ByteBuf byteBuf = Unpooled.wrappedBuffer(bytes);
        ByteBuf copy = byteBuf.copy();

        ByteBufUtils.log("", byteBuf);
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
        ByteBufUtils.log("", byteBuf);
        System.out.println("ByteBufUtils.arr(copy) = " + ByteBufUtils.arr(copy));

        bytes[10] = 0x6c;
        System.out.println("ByteBufUtils.arr(byteBuf) = " + ByteBufUtils.arr(byteBuf));
        System.out.println("ByteBufUtils.arr(copy) = " + ByteBufUtils.arr(copy));
    }
}
