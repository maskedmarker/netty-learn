# ByteBuf

## ByteBuf doc

```text
A random and sequential accessible sequence of zero or more bytes (octets). 
This interface provides an abstract view for one or more primitive byte arrays (byte[]) and NIO buffers.

Just like an ordinary primitive byte array, ByteBuf uses zero-based indexing.

ByteBuf provides two pointer variables to support sequential read and write operations - readerIndex for a read operation and writerIndex for a write operation respectively. 
The following diagram shows how a buffer is segmented into three areas by the two pointers:
       +-------------------+------------------+------------------+
       | discardable bytes |  readable bytes  |  writable bytes  |
       |                   |     (CONTENT)    |                  |
       +-------------------+------------------+------------------+
       |                   |                  |                  |
       0      <=      readerIndex   <=   writerIndex    <=    capacity
       
Readable bytes (the actual content) is where the actual data is stored. 
Writable bytes is a undefined space which needs to be filled.
Discardable bytes contains the bytes which were read already by a read operation.
```

```text
// Adjusts the capacity of this buffer. (ByteBuf可以有限扩容)
// If the newCapacity is less than the current capacity, the content of this buffer is truncated.  If the newCapacity is greater than the current capacity, the buffer is appended with unspecified data whose length is (newCapacity - currentCapacity).
// Throws: IllegalArgumentException – if the newCapacity is greater than maxCapacity() 虽然可以动态调整ByteBuf的容量,但是有上限的,即maxCapacity()
public abstract ByteBuf capacity(int newCapacity);

// Returns the maximum allowed capacity of this buffer. This value provides an upper bound on capacity().
public abstract int maxCapacity();
```

```text
// 记录“这个对象在什么地方被访问过” touch本身不会改变引用计数,也不会影响对象生命周期,只是给ResourceLeakDetector提供调试信息.Netty在泄漏报告中会打印这个hint
ReferenceCounted touch(Object hint);

因为Netty会操作直接内存,如果发生内存泄露,GC日志无法告知哪里发生了泄露,touch方法提供了访问轨迹.

只有开启LeakDetector才有意义,touch方法依赖LeakDetector.
-Dio.netty.leakDetectionLevel=disabled
-Dio.netty.leakDetectionLevel=simple
-Dio.netty.leakDetectionLevel=advanced
-Dio.netty.leakDetectionLevel=paranoid

很多Netty 组件会在关键阶段调用touch(hint)来记录ByteBuf在哪里被用过.
```


## 子类

```text
ByteBuf 
	AbstractByteBuf 
		AbstractDerivedByteBuf 
			AbstractUnpooledSlicedByteBuf
				UnpooledSlicedByteBuf
				SlicedByteBuf
		AbstractReferenceCountedByteBuf 
			AbstractPooledDerivedByteBuf 
			CompositeByteBuf 
			ReadOnlyByteBufferBuf 
			AdaptiveByteBuf in AdaptivePoolingAllocator 
			FixedCompositeByteBuf 
			PooledByteBuf 
			UnpooledDirectByteBuf 
			UnpooledHeapByteBuf 
	EmptyByteBuf 
	ReplayingDecoderByteBuf (io.netty.handler.codec)
	WrappedByteBuf 
		UnreleasableByteBuf 
		Component in FixedCompositeByteBuf 
		SimpleLeakAwareByteBuf 
		AdvancedLeakAwareByteBuf 
```