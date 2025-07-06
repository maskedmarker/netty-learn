# netty为了性能而新增的类

## FastThreadLocal
```text
Netty 创建 FastThreadLocal 的目的是为了解决标准 Java ThreadLocal 在高并发场景下的性能瓶颈，特别是在频繁访问和线程切换时。
FastThreadLocal 通过 空间换时间 的设计，显著提升了线程局部变量的访问速度，同时减少了内存开销。


1. 标准 ThreadLocal 的痛点
Java 原生的 ThreadLocal 存在以下问题：
哈希表性能开销：
    每个线程通过 ThreadLocalMap（线性探测哈希表）存储变量，get()/set() 需要计算哈希槽，可能触发冲突和扩容。
内存泄漏风险：
    ThreadLocal 的键是弱引用（WeakReference），但值可能因线程长期存活而泄漏，需手动 remove()。
线程切换成本高：
    线程池场景下，线程复用可能导致旧数据残留（需清理 ThreadLocal 状态）。
    
    
2. FastThreadLocal 的优化设计
Netty 的 FastThreadLocal 通过以下改进解决上述问题：

(1) 直接索引定位
数组存储替代哈希表：
    每个 FastThreadLocal 实例分配一个唯一索引（index），线程通过数组（InternalThreadLocalMap）直接访问变量，时间复杂度 O(1)，无哈希冲突。    
(2) 线程局部存储优化
专用线程类型支持：
配合 FastThreadLocalThread（Netty 扩展的线程类）使用，避免竞争。
    普通线程：退化到类似 ThreadLocal 的兼容模式。
    FastThreadLocalThread：直接操作内部数组，性能最优。    
(3) 内存管理
自动扩容数组：
    初始大小 32，按需扩容（类似 ArrayList），避免频繁分配。
快速清理：
    线程结束时自动清空数组（通过 FastThreadLocalRunnable 钩子），减少内存泄漏。    
(4) 消除伪共享
缓存行填充：
    数组元素间隔存储（padding），避免多线程修改相邻元素时的伪共享（False Sharing）。    
    
3. 性能对比
特性	       ThreadLocal (JDK)	       FastThreadLocal (Netty)
存储结构	    哈希表（线性探测）	            数组（直接索引）
访问速度	    O(n) 最坏情况（哈希冲突）	    O(1) 恒定时间
内存开销	    较高（哈希表额外开销）	        较低（数组紧凑存储）
线程兼容性	所有线程	                    最佳配合 FastThreadLocalThread
清理机制	    依赖弱引用（需手动 remove）	    自动清理（线程结束时）    

总结
Netty 设计 FastThreadLocal 的核心目的是：
    提升访问速度：通过数组索引替代哈希表，实现 O(1) 访问。
    降低内存开销：紧凑存储 + 自动清理，避免弱引用问题。
    适配高并发场景：尤其适合 Netty 的 EventLoop 线程模型。
这种优化使得 Netty 在处理网络 I/O 等高频操作时，能更高效地管理线程局部状态（如请求上下文、缓冲区等），成为其高性能框架的重要基石之一。
```

```text
java.lang.ThreadLocal.get
    public T get() {
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null) {
            ThreadLocalMap.Entry e = map.getEntry(this);
            if (e != null) {
                @SuppressWarnings("unchecked")
                T result = (T)e.value;
                return result;
            }
        }
        
        return setInitialValue();
    }
    
    private T setInitialValue() {
        T value = initialValue();
        Thread t = Thread.currentThread();
        ThreadLocalMap map = getMap(t);
        if (map != null)
            map.set(this, value);
        else
            // 首次调用会触发初始化ThreadLocalMap(还可能发生扩容)
            createMap(t, value);
        return value;
    }    
```