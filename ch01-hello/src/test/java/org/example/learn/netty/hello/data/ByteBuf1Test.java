package org.example.learn.netty.hello.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import org.example.learn.netty.hello.proxy.util.ByteBufUtils;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * ByteBuf是netty大量使用的基础数据结构
 * 不仅是对java nio的ByteBuffer的封装,还有了许多各具特色的子类
 */
public class ByteBuf1Test {

    /**
     *  It is recommended to create a new buffer using the helper methods in Unpooled rather than calling an individual implementation's constructor.
     *  建议通过Unpooled的静态方法创建ByteBuf对象,而非通过构造函数创建对象
     *
     *
     *  Unpooled的静态方法创建的ByteBuf对象是游离态的(非pooled),且是堆内(in-heap, 非堆外out-heap)对象,可以自动扩容,且是big-endian
     */
    @Test
    public void test01() {
        ByteBuf buf = Unpooled.buffer();   // 默认大小是256字节,可以自动扩容,且是big-endian
        Assert.assertTrue("Unpooled.buffer()返回的ByteBuf是unpooled对象,且是堆内in-heap,非堆外out-heap", buf instanceof UnpooledHeapByteBuf);

        int initCap = buf.capacity();
        Assert.assertEquals("Unpooled.buffer()返回的ByteBuf是unpooled对象,且是in-heap, 默认大小是256字节", 256, initCap);
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        Assert.assertEquals("Unpooled.buffer()返回的ByteBuf是unpooled对象,且是in-heap, 默认大小是256字节", 256, initCap);
        buf.writeByte(0);
        Assert.assertEquals("Unpooled.buffer()返回的ByteBuf是unpooled对象,且是in-heap, 默认大小是256字节,可以自动翻倍扩容", 256 * 2, buf.capacity());
        Assert.assertEquals("Unpooled.buffer()返回的ByteBuf是unpooled对象,且是in-heap, 默认大小是256字节,可以自动翻倍扩容", 256 + 1, buf.readableBytes());
    }


    /**
     * 默认big-endian(先写入的在低index中保存)
     */
    @Test
    public void test02() throws Exception {
        ByteBuf buf = Unpooled.buffer(); // 默认big-endian
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        // 默认big-endian,按照写入的顺序存储,先写入的在低index中保存
        for (int i = 0; i < 256; i++) {
            Assert.assertEquals("Unpooled.buffer()返回的ByteBuf是big-endian", (i & 0xFF), (buf.getByte(i) & 0xFF));  // getByte不改动读写指针readerIndex/writerIndex
        }
        log("默认big-endian,写入数据后", buf);


        for (int i = 0; i < 256; i++) {
            if (i < (256 - 1)) {
                Assert.assertEquals("ByteBuf存储的byte,当取一次取出多个字节时(short/int/long),需要考虑大小端(即字节的排列顺序)", (i << 8) | (i + 1), (buf.getShort(i) & 0xFFFF));  // & 0xFFFF主要是为了处理最高位的1
                Assert.assertEquals("ByteBuf存储的byte,当取一次取出多个字节时(short/int/long),需要考虑大小端(即字节的排列顺序)",  (i + 1) << 8 | i, (buf.getShortLE(i) & 0xFFFF));  // & 0xFFFF主要是为了处理最高位的1
            }

            if (i < (256 - 3)) {
                Assert.assertEquals("ByteBuf存储的byte,当取一次取出多个字节时(short/int/long),需要考虑大小端(即字节的排列顺序)", (((long) i) << 24) | ((i + 1) << 16) | ((i + 2) << 8) | (i + 3), (buf.getInt(i) & 0xFFFFFFFFL));
                Assert.assertEquals("ByteBuf存储的byte,当取一次取出多个字节时(short/int/long),需要考虑大小端(即字节的排列顺序)", (((long) (i + 3)) << 24) | ((i + 2) << 16) | ((i + 1) << 8) | i, (buf.getIntLE(i) & 0xFFFFFFFFL));
            }
        }
    }

    /**
     * 创建ByteBuf的副本(公用同一个底层数据)
     */
    @Test
    public void test031() throws Exception {
        ByteBuf buf = Unpooled.buffer(); // 默认big-endian
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        log("本体写入数据后", buf);
        buf.readByte();
        log("本体读取一个字节数据后", buf);


        // 副本和本体公用同一个底层数据,改动任何一个都影响另一个;副本和本体拥有各自独立的读写指针
        ByteBuf duplicate = buf.duplicate();
        Assert.assertEquals("副本和本体公用同一个底层数据", buf.array(), duplicate.array());

        log("副本初始状态", duplicate);
        Assert.assertEquals("副本与本体拥有各自独立的读写指针,读写指针初始值与本体相同", buf.readerIndex(), duplicate.readerIndex());
        duplicate.writeInt(256);
        log("本体保持无操作", buf);
        log("副本写入2个字节后", duplicate);
        Assert.assertEquals("副本与本体拥有各自独立的读写指针,读写指针初始值与本体相同", buf.readerIndex(), duplicate.readerIndex());
        Assert.assertNotEquals("副本与本体拥有各自独立的读写指针,读写指针初始值与本体相同", buf.writerIndex(), duplicate.writerIndex());
    }

    /**
     * 创建ByteBuf的副本(不公用同一个底层数据)
     */
    @Test
    public void test032() throws Exception {
        ByteBuf buf = Unpooled.buffer(); // 默认big-endian
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        log("本体写入数据后", buf);
        buf.readByte();
        log("本体读取一个字节数据后", buf);


        // 副本和本体不公用同一个底层数据,改动任何一个都不影响另一个;副本和本体拥有各自独立的读写指针
        ByteBuf duplicate = buf.copy();
        log("副本初始状态", duplicate);
        Assert.assertEquals("副本复制的是本体的readable-bytes,所以副本初始容量也会不同", buf.readableBytes(), duplicate.readableBytes());
        Assert.assertEquals("副本复制的是本体的readable-bytes,所以副本初始容量也会不同", buf.capacity() - 1, duplicate.capacity());
        Assert.assertEquals("副本与本体拥有各自独立的读写指针,副本读指针初始值为0", 0, duplicate.readerIndex());
        Assert.assertNotEquals("副本与本体拥有各自独立的读写指针,副本写指针也无需与本体保持相同,读写指针仅保证readable-bytes含义相同", buf.writerIndex(), duplicate.writerIndex());


        duplicate.writeInt(256);
        log("本体保持无操作", buf);
        log("副本写入2个字节后", duplicate);
    }

    /**
     * 清理ByteBuf
     */
    @Test
    public void test041() throws Exception {
        ByteBuf buf = Unpooled.buffer(); // 默认big-endian
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        log("写入数据后", buf);

        buf.clear();
        log("clear()后", buf);
        Assert.assertEquals("clear()会清除readable-bytes", buf.capacity(), buf.writableBytes());
        Assert.assertEquals("clear()会清除readable-bytes", 0, buf.readerIndex());
        Assert.assertEquals("clear()会清除readable-bytes", 0, buf.writerIndex());
    }

    /**
     * 清理ByteBuf
     */
    @Test
    public void test042() throws Exception {
        ByteBuf buf = Unpooled.buffer(); // 默认big-endian
        for (int i = 0; i < 256; i += 8) {
            buf.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        log("写入数据后", buf);

        buf.readByte();
        log("读取一个字节数据后", buf);


        buf.discardReadBytes();
        log("discardReadBytes数据后", buf);
        Assert.assertEquals("discardReadBytes会将readable-bytes移动到起始位置", 0, buf.readerIndex());
        Assert.assertEquals("discardReadBytes会将readable-bytes移动到起始位置", buf.capacity() - 1, buf.readableBytes());
    }

    /**
     * 从一个ByteBuf读取数据到另一个ByteBuf
     * readerIndex指的是当前可读取的第一个字节的index,writerIndex指的是当前可以写入的第一个字节的index
     */
    @Test
    public void test051() throws Exception {
        ByteBuf buf1 = Unpooled.buffer(); // 默认big-endian
        ByteBuf buf2 = Unpooled.buffer();
        for (int i = 0; i < 256; i += 8) {
            buf1.writeBytes(new byte[] {(byte)i, (byte)(i+1), (byte)(i+2), (byte)(i+3), (byte)(i+4), (byte)(i+5), (byte)(i+6), (byte)(i+7)});
        }
        log("写入数据后的buf1", buf1);
        log("未写入数据的buf2", buf2);
        Assert.assertEquals("buf1写满数据后,readerIndex在起始位置0", 0, buf1.readerIndex());
        Assert.assertEquals("buf1写满数据后,writerIndex在out-of-index的位置", 256, buf1.writerIndex());
        Assert.assertEquals("buf2初始化后,readerIndex在起始位置0", 0, buf2.readerIndex());
        Assert.assertEquals("buf2初始化后,writerIndex也在起始位置0", 0, buf2.writerIndex());

        buf2.writeBytes(buf1);
        log("从buf1读取数据后的buf1", buf1);
        log("从buf1读取数据后的buf2", buf2);
        Assert.assertEquals("从buf1批量读取数据后,buf1的readerIndex改变,buf1的writerIndex不改变", 256, buf1.readerIndex());
        Assert.assertEquals("从buf1批量读取数据后,buf1的readerIndex改变,buf1的writerIndex不改变", 256, buf1.writerIndex());
        Assert.assertEquals("从buf1批量读取数据后,buf2的readerIndex不改变,buf2的writerIndex改变", 0, buf2.readerIndex());
        Assert.assertEquals("从buf1批量读取数据后,buf2的readerIndex不改变,buf2的writerIndex改变", 256, buf2.writerIndex());
    }


    /**
     *  建议通过Unpooled的静态方法创建堆外(out-heap)ByteBuf对象(游离态的,非pooled)
     *  游离态的堆外ByteBuf对象需要手动释放内存
     */
    @Test
    public void test11() throws Exception {
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


    private void log(String info, ByteBuf buf) {
        ByteBufUtils.log(info, buf);
    }
}
