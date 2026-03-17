# example-explain-i/o


## write

样例代码
```text
channel.writeAndFlush(request);
```

io.netty.channel.Channel.write(msg)仅仅是将msg添加到outboundBuffer中,并非写入到socket中;io.netty.channel.Channel.flush()则是将outboundBuffer的数据写入到socket中.
如果因为socket-output-buffer没有足够的空间导致部分数据无法写入,会让selector监听OP_WRITE事件来知道什么时候socket-output-buffer又有写空间了,然后写入剩余待写入的数据.

```text
io.netty.channel.AbstractChannel.writeAndFlush(java.lang.Object)
    io.netty.channel.DefaultChannelPipeline.writeAndFlush(java.lang.Object)
        io.netty.channel.AbstractChannelHandlerContext.writeAndFlush(java.lang.Object)
            io.netty.channel.AbstractChannelHandlerContext.write(java.lang.Object, boolean, io.netty.channel.ChannelPromise)
                io.netty.channel.AbstractChannelHandlerContext.invokeWriteAndFlush
                    io.netty.channel.AbstractChannelHandlerContext.invokeWrite0
                        io.netty.channel.DefaultChannelPipeline.HeadContext.write
                            io.netty.channel.AbstractChannel.AbstractUnsafe.write
                    io.netty.channel.AbstractChannelHandlerContext.invokeFlush0
                    

io.netty.channel.DefaultChannelPipeline.writeAndFlush(java.lang.Object)
    public final ChannelFuture writeAndFlush(Object msg) {
        return tail.writeAndFlush(msg);
    }  
    
io.netty.channel.AbstractChannelHandlerContext.write(java.lang.Object, boolean, io.netty.channel.ChannelPromise)
    private void write(Object msg, boolean flush, ChannelPromise promise) {
        ObjectUtil.checkNotNull(msg, "msg");
        try {
            if (isNotValidPromise(promise, true)) {
                ReferenceCountUtil.release(msg);
                // cancelled
                return;
            }
        } catch (RuntimeException e) {
            ReferenceCountUtil.release(msg);
            throw e;
        }

        final AbstractChannelHandlerContext next = findContextOutbound(flush ?
                (MASK_WRITE | MASK_FLUSH) : MASK_WRITE);
        final Object m = pipeline.touch(msg, next);
        EventExecutor executor = next.executor();
        if (executor.inEventLoop()) {
            if (flush) {
                next.invokeWriteAndFlush(m, promise);
            } else {
                next.invokeWrite(m, promise);
            }
        } else {
            final WriteTask task = WriteTask.newInstance(next, m, promise, flush); // 💯💯💯 外部调用channel.write(msg)时,write操作是一个异步任务
            if (!safeExecute(executor, task, promise, m, !flush)) {
                // We failed to submit the WriteTask. We need to cancel it so we decrement the pending bytes and put it back in the Recycler for re-use later.
                task.cancel();
            }
        }
    }      
    

io.netty.channel.AbstractChannelHandlerContext.invokeWriteAndFlush    
    void invokeWriteAndFlush(Object msg, ChannelPromise promise) {
        if (invokeHandler()) {
            invokeWrite0(msg, promise);
            invokeFlush0();
        } else {
            writeAndFlush(msg, promise);
        }
    }
    
    
io.netty.channel.AbstractChannelHandlerContext.invokeWrite0
    private void invokeWrite0(Object msg, ChannelPromise promise) {
        try {
            final ChannelHandler handler = handler();
            final DefaultChannelPipeline.HeadContext headContext = pipeline.head;
            if (handler == headContext) {
                headContext.write(this, msg, promise);
            } else if (handler instanceof ChannelDuplexHandler) {
                ((ChannelDuplexHandler) handler).write(this, msg, promise);
            } else if (handler instanceof ChannelOutboundHandlerAdapter) {
                ((ChannelOutboundHandlerAdapter) handler).write(this, msg, promise);
            } else {
                ((ChannelOutboundHandler) handler).write(this, msg, promise);
            }
        } catch (Throwable t) {
            notifyOutboundHandlerException(t, promise);
        }
    }
    
io.netty.channel.DefaultChannelPipeline.HeadContext.write        

io.netty.channel.AbstractChannel.AbstractUnsafe.write
        public final void write(Object msg, ChannelPromise promise) {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                try {
                    // release message now to prevent resource-leak
                    ReferenceCountUtil.release(msg);
                } finally {
                    // If the outboundBuffer is null we know the channel was closed and so need to fail the future right away. If it is not null the handling of the rest will be done in flush0()
                    safeSetFailure(promise,newClosedChannelException(initialCloseCause, "write(Object, ChannelPromise)"));
                }
                return;
            }

            int size;
            try {
                msg = filterOutboundMessage(msg);
                size = pipeline.estimatorHandle().size(msg);
                if (size < 0) {
                    size = 0;
                }
            } catch (Throwable t) {
                try {
                    ReferenceCountUtil.release(msg);
                } finally {
                    safeSetFailure(promise, t);
                }
                return;
            }

            outboundBuffer.addMessage(msg, size, promise);  // 💯💯💯 write操作仅仅事件msg添加到buffer中去,并没有写入到socket中.只有flush操作才会真正将数据写入socket.(outboundBuffer超过高水位也会触发flush)
        }


io.netty.channel.AbstractChannelHandlerContext.invokeFlush0        
    private void invokeFlush0() {
        try {
            final ChannelHandler handler = handler();
            final DefaultChannelPipeline.HeadContext headContext = pipeline.head;
            if (handler == headContext) {
                headContext.flush(this);
            } else if (handler instanceof ChannelDuplexHandler) {
                ((ChannelDuplexHandler) handler).flush(this);
            } else if (handler instanceof ChannelOutboundHandlerAdapter) {
                ((ChannelOutboundHandlerAdapter) handler).flush(this);
            } else {
                ((ChannelOutboundHandler) handler).flush(this);
            }
        } catch (Throwable t) {
            invokeExceptionCaught(t);
        }
    }


io.netty.channel.AbstractChannel.AbstractUnsafe.flush
// Add a flush to this ChannelOutboundBuffer. This means all previous added messages are marked as flushed and so you will be able to handle them.    
        public final void flush() {
            assertEventLoop();

            ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null) {
                return;
            }

            outboundBuffer.addFlush();
            flush0();
        }  
        
        
        
io.netty.channel.AbstractChannel.AbstractUnsafe.flush0
        protected void flush0() {
            if (inFlush0) {
                // Avoid re-entrance
                return;
            }

            final ChannelOutboundBuffer outboundBuffer = this.outboundBuffer;
            if (outboundBuffer == null || outboundBuffer.isEmpty()) {
                return;
            }

            inFlush0 = true;

            // Mark all pending write requests as failure if the channel is inactive.
            if (!isActive()) {
                try {
                    // Check if we need to generate the exception at all.
                    if (!outboundBuffer.isEmpty()) {
                        if (isOpen()) {
                            outboundBuffer.failFlushed(new NotYetConnectedException(), true);
                        } else {
                            // Do not trigger channelWritabilityChanged because the channel is closed already.
                            outboundBuffer.failFlushed(newClosedChannelException(initialCloseCause, "flush0()"), false);
                        }
                    }
                } finally {
                    inFlush0 = false;
                }
                return;
            }

            try {
                doWrite(outboundBuffer);
            } catch (Throwable t) {
                handleWriteError(t);
            } finally {
                inFlush0 = false;
            }
        }
        
        
        
io.netty.channel.socket.nio.NioSocketChannel.doWrite
    protected void doWrite(ChannelOutboundBuffer in) throws Exception {
        SocketChannel ch = javaChannel();
        int writeSpinCount = config().getWriteSpinCount();
        do {
            if (in.isEmpty()) {
                // All written so clear OP_WRITE
                clearOpWrite();
                // Directly return here so incompleteWrite(...) is not called.
                return;
            }

            // Ensure the pending writes are made of ByteBufs only.
            int maxBytesPerGatheringWrite = ((NioSocketChannelConfig) config).getMaxBytesPerGatheringWrite();
            ByteBuffer[] nioBuffers = in.nioBuffers(1024, maxBytesPerGatheringWrite);
            int nioBufferCnt = in.nioBufferCount();

            // Always use nioBuffers() to workaround data-corruption.
            // See https://github.com/netty/netty/issues/2761
            switch (nioBufferCnt) {
                case 0:
                    // We have something else beside ByteBuffers to write so fallback to normal writes.
                    writeSpinCount -= doWrite0(in);
                    break;
                case 1: {
                    // Only one ByteBuf so use non-gathering write Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need to check if the total size of all the buffers is non-zero.
                    ByteBuffer buffer = nioBuffers[0];
                    int attemptedBytes = buffer.remaining();
                    final int localWrittenBytes = ch.write(buffer);       //💯💯💯 通过java的nio接口写入数据
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);                           // 💯💯💯如果本次写入因为socket-output-buffer因为没有足够的空间导致部分数据无法写入,会让selector监听OP_WRITE事件
                        return;
                    }
                    adjustMaxBytesPerGatheringWrite(attemptedBytes, localWrittenBytes, maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
                default: {
                    // Zero length buffers are not added to nioBuffers by ChannelOutboundBuffer, so there is no need to check if the total size of all the buffers is non-zero.
                    // We limit the max amount to int above so cast is safe
                    long attemptedBytes = in.nioBufferSize();
                    final long localWrittenBytes = ch.write(nioBuffers, 0, nioBufferCnt);
                    if (localWrittenBytes <= 0) {
                        incompleteWrite(true);                             // 💯💯💯如果本次写入因为socket-output-buffer因为没有足够的空间导致部分数据无法写入,会让selector监听OP_WRITE事件
                        return;
                    }
                    // Casting to int is safe because we limit the total amount of data in the nioBuffers to int above.
                    adjustMaxBytesPerGatheringWrite((int) attemptedBytes, (int) localWrittenBytes,
                            maxBytesPerGatheringWrite);
                    in.removeBytes(localWrittenBytes);
                    --writeSpinCount;
                    break;
                }
            }
        } while (writeSpinCount > 0);

        incompleteWrite(writeSpinCount < 0);                
    }

    
io.netty.channel.nio.AbstractNioByteChannel.incompleteWrite   
    protected final void incompleteWrite(boolean setOpWrite) {
        // Did not write completely.
        if (setOpWrite) {
            setOpWrite();                                                                     // interestOps设置OP_WRITE事件
        } else {
            // Schedule flush again later so other tasks can be picked up in the meantime
            Runnable flushTask = this.flushTask;
            if (flushTask == null) {
                flushTask = this.flushTask = new Runnable() {
                    @Override
                    public void run() {
                        flush();
                    }
                };
            }
            eventLoop().execute(flushTask);
        }
    } 
```



















