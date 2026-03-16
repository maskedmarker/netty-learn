package org.example.learn.netty.hello.data;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * ByteBuf使用了池化技术(PooledByteBuf)
 * PooledDirectByteBuf/PooledHeapByteBuf
 * PooledUnsafeDirectByteBuf/PooledUnsafeHeapByteBuf
 *
 *
 */
public class PooledByteBufTest {

    /**
     * 在Netty中获取PooledByteBuf通常不直接通过构造函数,而是通过 PooledByteBufAllocator 分配器来获取
     */
    @Test
    public void test01() {
        ByteBufAllocator allocator = ByteBufAllocator.DEFAULT;    // 使用默认分配器（Netty 4.x+ 默认是池化+直接内存）
        System.out.println("默认分配器类型: " + allocator.getClass().getSimpleName());


        ByteBufAllocator pooledAlloc = PooledByteBufAllocator.DEFAULT;  // 明确使用池化分配器（推荐直接使用这个，语义清晰）

        ByteBuf heapBuffer1 = allocator.buffer(256);
        ByteBuf heapBuffer2 = pooledAlloc.buffer(256);

        System.out.println("heapBuffer1.getClass().getSimpleName() = " + heapBuffer1.getClass().getSimpleName());
        System.out.println("heapBuffer2.getClass().getSimpleName() = " + heapBuffer2.getClass().getSimpleName());

        // 池化意味着用完要还回去，否则内存泄漏
        heapBuffer1.release();
        heapBuffer2.release();
    }

    @Test
    public void test02() {
        ByteBufAllocator pooledAlloc = PooledByteBufAllocator.DEFAULT;

        ByteBuf heapBuffer = pooledAlloc.heapBuffer(256); // 初始容量256, 堆内内存
        ByteBuf directBuffer = pooledAlloc.directBuffer(512); // 初始容量512, 堆外内存

        heapBuffer.writeBytes("Hello Netty".getBytes());
        directBuffer.writeBytes("Hello Netty".getBytes());
        System.out.println(heapBuffer.toString(io.netty.util.CharsetUtil.UTF_8));
        System.out.println(directBuffer.toString(io.netty.util.CharsetUtil.UTF_8));

        // 池化意味着用完要还回去，否则内存泄漏
        directBuffer.release();
        heapBuffer.release();
    }

    @Test
    public void test03() {
        ByteBufAllocator pooledAlloc = PooledByteBufAllocator.DEFAULT;
        ByteBuf buffer = pooledAlloc.buffer(256);    // 由jvm的能力决定 以及用户也可以自主控制(io.netty.noPreferDirect)

        buffer.release();
    }
}
