# NioEventLoop

## 关键属性

```text
public final class NioEventLoop extends SingleThreadEventLoop {   // NioEventLoop由一个工作线程来实现event-loop
    private Selector selector;
    private Selector unwrappedSelector;
    private final SelectorProvider provider;
    
    private SelectedSelectionKeySet selectedKeys;
    
    private final AtomicLong nextWakeupNanos = new AtomicLong(AWAKE);
    private final SelectStrategy selectStrategy;
}

public abstract class SingleThreadEventLoop extends SingleThreadEventExecutor implements EventLoop {
    private final Queue<Runnable> tailTasks;
}

public abstract class SingleThreadEventExecutor extends AbstractScheduledEventExecutor implements OrderedEventExecutor {
    private final Queue<Runnable> taskQueue;
    private final Executor executor;  // 执行event-loop任务的线程池(只有一个工作线程)
    
    private volatile Thread thread;   // 当前执行event-loop的工作线程(inEventLoop方法会用到该属性)
    
    private final Promise<?> terminationFuture = new DefaultPromise<Void>(GlobalEventExecutor.INSTANCE);   // 当前EventExecutor终止时,会被set
}
```

## run

NioEventLoop的thread会在run方法中无穷loop

```text
protected void run() {
    int selectCnt = 0;
    
    // 线程通过不断地循环来响应不同事件
    for (;;) {
        try {
            int strategy;
            try {
                strategy = selectStrategy.calculateStrategy(selectNowSupplier, hasTasks());         // hasTasks()返回true则处理SelectStrategy.SELECT,hasTasks()返回false则处理SelectStrategy.CONTINUE(即重新loop)
                switch (strategy) {
                case SelectStrategy.CONTINUE:
                    continue;

                case SelectStrategy.BUSY_WAIT:
                    // fall-through to SELECT since the busy-wait is not supported with NIO

                case SelectStrategy.SELECT:
                    long curDeadlineNanos = nextScheduledTaskDeadlineNanos();
                    if (curDeadlineNanos == -1L) {
                        curDeadlineNanos = NONE; // nothing on the calendar
                    }
                    nextWakeupNanos.set(curDeadlineNanos);
                    try {
                        if (!hasTasks()) {
                            strategy = select(curDeadlineNanos);
                        }
                    } finally {
                        // This update is just to help block unnecessary selector wakeups
                        // so use of lazySet is ok (no race condition)
                        nextWakeupNanos.lazySet(AWAKE);
                    }
                    // fall through
                default:
                }
            } catch (IOException e) {
                // If we receive an IOException here its because the Selector is messed up. Let's rebuild
                // the selector and retry. https://github.com/netty/netty/issues/8566
                rebuildSelector0();
                selectCnt = 0;
                handleLoopException(e);
                continue;
            }

            selectCnt++;
            cancelledKeys = 0;
            needsToSelectAgain = false;
            final int ioRatio = this.ioRatio;
            boolean ranTasks;
            if (ioRatio == 100) {
                try {
                    if (strategy > 0) {
                        processSelectedKeys();
                    }
                } finally {
                    // Ensure we always run tasks.
                    ranTasks = runAllTasks();
                }
            } else if (strategy > 0) {
                final long ioStartTime = System.nanoTime();
                try {
                    processSelectedKeys();
                } finally {
                    // Ensure we always run tasks.
                    final long ioTime = System.nanoTime() - ioStartTime;
                    ranTasks = runAllTasks(ioTime * (100 - ioRatio) / ioRatio);
                }
            } else {
                ranTasks = runAllTasks(0); // This will run the minimum number of tasks
            }

            if (selectReturnPrematurely(selectCnt, ranTasks, strategy)) {
                selectCnt = 0;
            } else if (unexpectedSelectorWakeup(selectCnt)) { // Unexpected wakeup (unusual case)
                selectCnt = 0;
            }
        } catch (CancelledKeyException e) {
            // Harmless exception - log anyway
            if (logger.isDebugEnabled()) {
                logger.debug(CancelledKeyException.class.getSimpleName() + " raised by a Selector {} - JDK bug?",
                        selector, e);
            }
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            handleLoopException(t);
        } finally {
            // Always handle shutdown even if the loop processing threw an exception.
            try {
                if (isShuttingDown()) {
                    closeAll();
                    if (confirmShutdown()) {
                        return;
                    }
                }
            } catch (Error e) {
                throw e;
            } catch (Throwable t) {
                handleLoopException(t);
            }
        }
    }
}
```

## processSelectedKey

写操作先将数据写入到ChannelOutboundBuffer,然后添加OP_WRITE.等channel满足OP_WRITE时,才将ChannelOutboundBuffer中的数据写入到socket中.
读操作先添加OP_READ,等channel满足OP_READ


```text
private void processSelectedKey(SelectionKey k, AbstractNioChannel ch) {
    final AbstractNioChannel.NioUnsafe unsafe = ch.unsafe();
    if (!k.isValid()) {
        final EventLoop eventLoop;
        try {
            eventLoop = ch.eventLoop();
        } catch (Throwable ignored) {
            // If the channel implementation throws an exception because there is no event loop, we ignore this
            // because we are only trying to determine if ch is registered to this event loop and thus has authority to close ch.
            return;
        }
        // Only close ch if ch is still registered to this EventLoop. ch could have deregistered from the event loop
        // and thus the SelectionKey could be cancelled as part of the deregistration process, but the channel is still healthy and should not be closed.
        // See https://github.com/netty/netty/issues/5125
        if (eventLoop == this) {
            // close the channel if the key is not valid anymore
            unsafe.close(unsafe.voidPromise());
        }
        return;
    }

    try {
        int readyOps = k.readyOps();

        if ((readyOps & SelectionKey.OP_CONNECT) != 0) {
            int ops = k.interestOps();
            ops &= ~SelectionKey.OP_CONNECT;   // 💯💯💯 OP_CONNECT底层实际向epoll注册的是可写事件,只要socket-output-buffer有空间就会触发.收到过一次OP_CONNECT事件后必须将其从interestOps移除,否则意味着向epoll注册了可写事件,select大多情况下都不会发生阻塞.
            k.interestOps(ops);
            
            // 💯 We first need to call finishConnect() before try to trigger a read(...) or write(...) as otherwise the NIO JDK channel implementation may throw a NotYetConnectedException.(Java-nio的api要求)
            unsafe.finishConnect();              // 💯💯💯 这里会向pipeline通知ChannelActive
        }

        // Process OP_WRITE first as we may be able to write some queued buffers and so free memory. (write操作通常写入到socket的outputBuffer中就行了,相对于read操作耗时比较短,所以优先处理写操作)
        if ((readyOps & SelectionKey.OP_WRITE) != 0) {
            // Call forceFlush which will also take care of clear the OP_WRITE once there is nothing left to write
           unsafe.forceFlush();                                            // 将待写入的数据写入到socket中,如果数据都写完了则清除OP_WRITE(后续再有写操作时,会重新设置OP_WRITE)
        }

        // Also check for readOps of 0 to workaround possible JDK bug which may otherwise lead to a spin loop
        // serverSocket的read-ready就是OP_ACCEPT,client-Socket的read-ready就是OP_READ
        if ((readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0 || readyOps == 0) {
            unsafe.read();                     // 💯💯💯 这里会触发fireChannelRead和fireChannelReadComplete事件 (server-socket的unsafe.read()可以读取到新accept的连接,新连接被当作msg来处理,由ServerBootstrapAcceptor特殊处理)
        }
    } catch (CancelledKeyException ignored) {
        unsafe.close(unsafe.voidPromise());
    }
}
```

## fireChannelActive

NioEventLoop轮询发现OP_CONNECT后,调用NioUnsafe.finishConnect,
NioUnsafe.finishConnect中会通过SocketChannel.finishConnect()确认已经建立TCP连接(jdk-api的要求,否则后续read/write操作可能会抛异常),然后才会向pipeline通知ChannelActive事件.

```text
io.netty.channel.nio.AbstractNioChannel.AbstractNioUnsafe.finishConnect
    io.netty.channel.nio.AbstractNioChannel.AbstractNioUnsafe.fulfillConnectPromise(io.netty.channel.ChannelPromise, boolean)
        io.netty.channel.ChannelPipeline.fireChannelActive
        
        
private void fulfillConnectPromise(ChannelPromise promise, boolean wasActive) {
    if (promise == null) {
        // Closed via cancellation and the promise has been notified already.
        return;
    }

    // Get the state as trySuccess() may trigger an ChannelFutureListener that will close the Channel.
    // We still need to ensure we call fireChannelActive() in this case.
    boolean active = isActive();

    // trySuccess() will return false if a user cancelled the connection attempt.
    boolean promiseSet = promise.trySuccess();

    // Regardless if the connection attempt was cancelled, channelActive() event should be triggered, because what happened is what happened.
    if (!wasActive && active) {
        pipeline().fireChannelActive();   //💯💯💯 这里会向pipeline通知ChannelActive
    }

    // If a user cancelled the connection attempt, close the channel, which is followed by channelInactive().
    if (!promiseSet) {
        close(voidPromise());
    }
}
```

## fireChannelRead和fireChannelReadComplete

```text
io.netty.channel.nio.AbstractNioByteChannel.NioByteUnsafe.read
    io.netty.channel.socket.nio.NioSocketChannel.doReadBytes
        io.netty.buffer.ByteBuf.writeBytes(java.nio.channels.ScatteringByteChannel in, int length)  (in就是Java-nio的SocketChannel, 从in读取数据并写入到ByteBuf中,最多读取length个字节)
        
          
public final void read() {
    final ChannelConfig config = config();
    if (shouldBreakReadReady(config)) {
        clearReadPending();
        return;
    }
    final ChannelPipeline pipeline = pipeline();
    final ByteBufAllocator allocator = config.getAllocator();
    final RecvByteBufAllocator.Handle allocHandle = recvBufAllocHandle();
    allocHandle.reset(config);

    ByteBuf byteBuf = null;
    boolean close = false;
    try {
        do {
            byteBuf = allocHandle.allocate(allocator);           // allocHandle会记录每次从socketChannel中期望读取的数据量与实际的数据量的差别,然后下次分派容量更合适的byteBuf,以其尽量尽量一次读取完socket input-buffer中的全部数据.
            allocHandle.lastBytesRead(doReadBytes(byteBuf));     // 💯按byteBuf的初始容量从socket-input-buffer读取数据到byteBuf,初始容量可能不足以一次读取完socket-input-buffer中的数据,所以需要while来多次读取
            if (allocHandle.lastBytesRead() <= 0) {
                // nothing was read. release the buffer.
                byteBuf.release();
                byteBuf = null;
                close = allocHandle.lastBytesRead() < 0;
                if (close) {
                    // There is nothing left to read as we received an EOF.
                    readPending = false;
                }
                break;
            }

            allocHandle.incMessagesRead(1);
            readPending = false;
            pipeline.fireChannelRead(byteBuf);                     // 💯💯💯当前将socket-input-buffer现有的数据因为无法评估大小,所以用多次读取,每读取一次就赶紧向pipeline通知fireChannelRead事件
            byteBuf = null;
        } while (allocHandle.continueReading());

        allocHandle.readComplete();
        pipeline.fireChannelReadComplete();                        // 💯💯💯当前将socket-input-buffer现有的数据读取完毕后,向pipeline通知fireChannelReadComplete事件

        if (close) {
            closeOnRead(pipeline);
        }
    } catch (Throwable t) {
        handleReadException(pipeline, byteBuf, t, close, allocHandle);
    } finally {
        // Check if there is a readPending which was not processed yet.
        // This could be for two reasons:
        // * The user called Channel.read() or ChannelHandlerContext.read() in channelRead(...) method
        // * The user called Channel.read() or ChannelHandlerContext.read() in channelReadComplete(...) method
        //
        // See https://github.com/netty/netty/issues/2254
        if (!readPending && !config.isAutoRead()) {
            removeReadOp();
        }
    }
}

io.netty.channel.socket.nio.NioSocketChannel.doReadBytes
protected int doReadBytes(ByteBuf byteBuf) throws Exception {
    final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
    allocHandle.attemptedBytesRead(byteBuf.writableBytes());                           // 根据byteBuf的可用容量(不涉及自动扩容)来从socket-input-buffer中读取数据,那么byteBuf初始容量就比较重要了
    return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());
}
```