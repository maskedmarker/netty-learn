# 池化技术

```text
PoolArena 可以理解为：一个“内存分配分区 + 分配策略执行者”

它在 Netty 内存池中的角色类似于 JVM 里的 TLAB + 分代管理的结合体。


✔ 1. 分区管理（核心目的：降低锁竞争）

Netty 会创建多个 PoolArena
每个线程（更准确是 PoolThreadCache）绑定一个 Arena
分配时优先在自己的 Arena 内操作



✔ 2. 分配策略决策中心

PoolArena 负责根据申请内存大小，决定走哪条路径：

类型	        大小范围	                    分配方式
Tiny	    < 512B	                    Subpage
Small	    < 8KB	                    Subpage
Normal	    ≤ chunkSize (默认16MB)	    Chunk + 二叉树
Huge	    > chunkSize	                直接分配（非池化）

👉 注意：
Tiny + Small → 走 Subpage
Normal → 走 Chunk（伙伴算法）
Huge → 绕过池
```

```text
PoolArena 内部结构（重点）


PoolArena 不是一个简单类，它内部维护了多个关键结构：

1. ChunkList（核心）
PoolArena
 ├── qInit
 ├── q000
 ├── q025
 ├── q050
 ├── q075
 └── q100

这些链表是按 Chunk 使用率分层的：

列表	    使用率
qInit	0~25%
q000	1~50%
q025	25~75%
q050	50~100%
q075	75~100%
q100	100%

👉 作用：
避免遍历所有 Chunk
快速定位“合适空闲空间”的 Chunk



2. PoolChunk（大块内存）

每个 Chunk 默认：16MB = 2^24

内部使用：完全二叉树（伙伴分配算法）来管理内存块：
[16MB]
 ├── 8MB
 │   ├── 4MB
 │   └── 4MB
 └── 8MB
 
 
3. Subpage（处理小内存）

用于 Tiny / Small：
8KB page
 ├── 16B
 ├── 16B
 ├── 16B
 ...

👉 本质：
bitmap 管理
类似 slab 分配器 
```

```text
分配流程



Step 1：进入 PoolArena
PooledByteBufAllocator
   → PoolArena.allocate()

Step 2：判断类型
1024B → Small

Step 3：优先走 ThreadCache（极重要）
PoolThreadCache → 是否有缓存？

👉 如果命中：
无锁
O(1)
👉 如果没命中：
才进入 Arena


Step 4：走 Subpage 分配
Arena → 找对应 sizeClass的Subpage
找 bitmap 空位
分配 index


Step 5：如果没有可用 Subpage
→ 从 Chunk 分裂 page
→ 创建新的 Subpage
```

```text
ByteBuf 回收链路


Netty 内存管理最核心的一环：ByteBuf 如何安全释放 + 如何回收到对象池复用

核心模型
ByteBuf 生命周期 = 引用计数（retain/release） + 对象回收（Recycler）




完整回收链路
ByteBuf.release()
  ↓
refCnt--
  ↓
refCnt == 0 ?
  ↓ yes
deallocate()
  ↓
├── 1. 内存归还
│     ↓
│   PoolArena.free()
│     ↓
│   ├── SubPage bitmap 释放
│   └── PoolChunk 二叉树合并
│
└── 2. 对象回收
      ↓
   Recycler.recycle()
      ↓
   ├── 同线程 → Stack
   └── 跨线程 → WeakOrderQueue
   
   
```

