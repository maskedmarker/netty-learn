# reactor模型

```text
主要分为:
单线程Reactor     (Single-Thread Reactor)
多线程Reactor     (Multi-Thread Reactor)
主从多线程Reactor  (Master-Slave Multi-Thread Reactor)
```

## Single-Thread Reactor

Architecture
```text
           ┌─────────────────┐
client ───▶│  Reactor        │
           │ (single thread) │
           │                 │
           │ select()        │        
           │ accept()        │        
           │ read()          │        
           │ write()         │        
           │ business logic  │
           └─────────────────┘
```

## Multi-Thread Reactor

Architecture
```text
                 ┌────────────────┐
                 │ Reactor thread │
client ────────▶ │ select()       │
                 │ accept/read    │
                 └───────┬────────┘
                         │
                         ▼
               ┌───────────────────┐
               │ Worker ThreadPool │
               │ business logic    │
               └───────────────────┘
```

## Master-Slave Multi-Thread Reactor

Architecture
```text
                ┌──────────────┐
client ───────▶ │  Main Reactor│
                │   (accept)   │
                └──────┬───────┘
                       │
                       ▼
             ┌────────────────────┐
             │ Sub Reactor Pool   │
             │ (multiple threads) │
             │ read / write       │
             └─────────┬──────────┘
                       │
                       ▼
             ┌────────────────────┐
             │  Worker Threads    │
             │   business logic   │
             └────────────────────┘
```

```text
ServerBootstrap bootstrap = new ServerBootstrap();
bootstrap.group(bossGroup, workerGroup);

bossGroup   指的就是main-reactor(可以多线程并发执行)
workerGroup 指的就是sub-reactor(可以多线程并发执行)
```