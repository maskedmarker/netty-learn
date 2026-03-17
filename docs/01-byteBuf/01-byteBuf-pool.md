# PooledByteBuf


## 池化技术

```text
池化技术(Pooled ByteBuf)

类似 jemalloc 算法(PoolThreadCache -> PoolArena -> PoolChunk).
通过 PooledByteBufAllocator 分配内存,减少频繁的内存创建和GC压力.

通过 NettyRefcountTrigger 监控或 -Dio.netty.leakDetectionLevel=advanced 检测.
```


```text
io.netty.buffer.PooledByteBufAllocator.newDirectBuffer

public class PooledByteBufAllocator extends AbstractByteBufAllocator implements ByteBufAllocatorMetricProvider {
    
    private final PoolThreadLocalCache threadCache;
    
    protected ByteBuf newDirectBuffer(int initialCapacity, int maxCapacity) {
        PoolThreadCache cache = threadCache.get();                                          // FastThreadLocal 线程级别的缓存
        PoolArena<ByteBuffer> directArena = cache.directArena;

        final ByteBuf buf;
        if (directArena != null) {
            buf = directArena.allocate(cache, initialCapacity, maxCapacity);                                   // 核心：通过 arena 分配
        } else {                                                                                               // 兜底：非池化(几乎不会走这里)
            buf = PlatformDependent.hasUnsafe() ?
                    UnsafeByteBufUtil.newUnsafeDirectByteBuf(this, initialCapacity, maxCapacity) :
                    new UnpooledDirectByteBuf(this, initialCapacity, maxCapacity);
        }

        return toLeakAwareBuffer(buf);                                                                         // 内存泄漏检测包装
    }
}      
```

```text
PoolThreadCache 内部结构
它内部维护了几个 MemoryRegionCache 数组,按照使用频率(tiny/small/normal)和类型(Direct/Heap)划分.
如果命中缓存：
直接从 MemoryRegionCache 中取出之前释放并缓存的 ByteBuf 对象(Handle),无需加锁,效率极高.
如果未命中缓存：
调用 PoolArena.allocate() 进入下一级.


// PoolThreadCache.java (简化)
final class PoolThreadCache {
    private final MemoryRegionCache<ByteBuffer>[] tinySubPageDirectCaches;
    private final MemoryRegionCache<ByteBuffer>[] smallSubPageDirectCaches;
    private final MemoryRegionCache<ByteBuffer>[] normalDirectCaches;
    // ... 还有 heap 版本

    // 分配时先尝试从 cache 拿
    boolean allocateTiny(PoolArena<?> area, PooledByteBuf<?> buf, int reqCapacity, int normCapacity) {
        return allocate(cacheForTiny(area, normCapacity), buf, reqCapacity);
    }
}
```

```text
PoolArena： arena 级分配器(第二站)

作用： 管理一大块内存,负责协调不同线程的分配请求.PoolArena 的数量通常 = CPU核心数 * 2,多个 PoolArena 之间通过轮询(PooledByteBufAllocator 内部的 arena 数组)来减少线程间的锁竞争.

// PoolArena.java
PooledByteBuf<T> allocate(PoolThreadCache cache, int reqCapacity, int maxCapacity) {
    // 根据请求大小规范化容量(如 513 会被规范化为 512 或 1024)
    int normCapacity = normalizeCapacity(reqCapacity);

    PooledByteBuf<T> buf = newByteBuf(maxCapacity); // 尝试从对象池复用(RECYCLER)
    if (allocate(cache, buf, normCapacity)) {
        // 分配成功
        return buf;
    }
    // ... 异常处理
}


private boolean allocate(PoolThreadCache cache, PooledByteBuf<T> buf, final int reqCapacity) {
    // 1. 极小的内存 (Tiny: < 512)
    if (reqCapacity <= tinyCacheSize) { // 512
        if (cache.allocateTiny(this, buf, reqCapacity, normCapacity)) {
            return true; // 从线程缓存拿到
        }
        // 从 SubPage 分配
        return tcache.allocateTiny(this, buf, reqCapacity, normCapacity);
    }

    // 2. 小型内存 (512 < size < pageSize)
    if (reqCapacity <= smallCacheSize) {
        if (cache.allocateSmall(this, buf, reqCapacity, normCapacity)) {
            return true;
        }
        // 从 SubPage 分配
        return sCache.allocateSmall(this, buf, reqCapacity, normCapacity);
    }

    // 3. 中型内存 (pageSize < size < chunkSize)
    if (normCapacity <= chunkSize) {
        if (cache.allocateNormal(this, buf, reqCapacity, normCapacity)) {
            return true;
        }
        // 从 Chunk 分配
        synchronized (this) { // 注意：Normal/Huge 需要加锁
            return allocateNormal(buf, reqCapacity, normCapacity);
        }
    }

    // 4. 巨型内存 (Huge) 直接分配非池化的(防止耗尽内存)
    return allocateHuge(buf, reqCapacity);
}

关键点：
Tiny/Small 内存： 如果线程缓存没有,会在 PoolArena 的 PoolSubPage 上分配(后续由 PoolChunk 管理).
Normal 内存： 直接由 PoolChunk 分配,需要加锁(因为多个线程可能同时操作同一个 PoolArena 的 Chunk 列表).
Huge 内存： 不池化,用完即扔.
```

```text
 PoolChunk：内存块管理者(第三站)
 
作用： 管理一段连续的内存(默认 16MB).它使用伙伴分配算法(Buddy Allocation) 和 PoolSubPage 来高效分配和释放内存.
```

```text
流程图解：一次分配请求的完整路径
假设当前线程是 EventLoop-1,请求 512 字节.

PoolThreadCache
    检查 tinySubPageDirectCaches[512] 是否有缓存对象？
    有 -> 直接取出 ByteBuf,无锁,分配结束.
    无 -> 调用 arena.allocate().

PoolArena (假设是 directArena[1])
请求大小 512 属于 Tiny.
    再次尝试从线程缓存获取(二次确认,其实第一步已确认).
    未命中 -> 进入 allocateTiny().
    找到对应的 PoolSubPage 列表(tinySubpagePools).
    如果列表中有空闲的 SubPage：
        调用 PoolSubpage.allocate() -> 获取内存块 -> 包装成 ByteBuf.
    如果没有空闲的 SubPage：
        调用 PoolChunk.allocateSubpage().

PoolChunk
    allocateNode(11)：从二叉树第 11 层(叶子节点,8KB)找一个空闲节点.
    假设找到节点 index = 2048(handle=2048).
    初始化 PoolSubpage,将 8KB 分割成 16 个 512 字节.
    分配第一个 512 字节,更新 SubPage 位图.
    返回该内存块的 handle(包含了 Chunk ID 和 SubPage 内的偏移量).

返回
    PoolArena 将 handle 包装成 PooledByteBuf 实例(从 RECYCLER 池中取出对象,复用).
    设置 buf.memory = chunk.memory, buf.handle = handle, buf.readerIndex/writerIndex 等.
    返回给用户.


释放过程(反向路径)

当调用 buf.release() 时：
归还给 PoolChunk：
    根据 handle 计算出属于哪个 Chunk 和 SubPage.
    如果是 SubPage 内存,标记 SubPage 的位图该位置为空.
    如果 SubPage 完全空闲,则将整个叶子节点归还给 Chunk 的二叉树(标记节点可用).
缓存到 PoolThreadCache：
    不直接销毁内存,而是将 handle 放入当前线程的 PoolThreadCache 对应的缓存槽中.
    下次该线程再分配同样大小的内存,直接从缓存拿,性能极高.
    
    
    
总结
    PoolThreadCache：线程私有,无锁缓存,最快.
    PoolArena：分区管理,减少锁竞争,负责决定分配策略(Tiny/Small/Normal/Huge).
    PoolChunk：物理内存持有者,使用伙伴算法 + SubPage 位图管理 16MB 内存块.    
```


