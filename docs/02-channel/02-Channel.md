# Channel相关


```text
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {}

public interface ChannelPipeline extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {}
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {}

final class HeadContext extends AbstractChannelHandlerContext implements ChannelOutboundHandler, ChannelInboundHandler {}
final class TailContext extends AbstractChannelHandlerContext implements ChannelInboundHandler {}

ChannelOutboundInvoker包含了bind/connect/read/write之类的向socket下达命令的操作.而ChannelOutboundInvoker主要是向内部组件通知事件.
Channel主要是向socket下达I/O类命令的,所以继承ChannelOutboundInvoker合情合理.而向内部组件通知事件的功能并没强加到Channel上,而是通过ChannelPipeline来完成.
```

```text
protected AbstractChannel(Channel parent) {
    this.parent = parent;
    id = newId();
    unsafe = newUnsafe();
    pipeline = newChannelPipeline();              // 在创建Channel对象时,创建与之紧密关联的ChannelPipeline💯💯💯
}

protected AbstractChannel(Channel parent, ChannelId id) {
    this.parent = parent;
    this.id = id;
    unsafe = newUnsafe();
    pipeline = newChannelPipeline();
}
```







## Channel.Unsafe

```text
Unsafe operations that should never be called from user-code. 
These methods are only provided to implement the actual transport, and must be invoked from an I/O thread, except for the following methods:
    localAddress()
    remoteAddress()
    closeForcibly()
    register(EventLoop, ChannelPromise)
    deregister(ChannelPromise)
    voidPromise()
    
    
```






### disconnect() 与 close()

```text
Method	                    Semantic intent
disconnect()	            Disconnect the channel from its remote peer
close()	                    Close the channel completely

Netty is transport-agnostic and designed to support: TCP/UDP/SCTP/UDT/Unix Domain Sockets/future transports.This difference exists because not all transports are connection-oriented.

For connection-oriented transports (TCP, SCTP), disconnect() ≡ close(), Closes the socket.
For connectionless transports (UDP),Clears the remote peer.


How this maps to OS behavior?
TCP example, disconnect() ≡ close()

UDP example (POSIX),Netty’s disconnect() maps to this:
connect(fd, remote);... connect(fd, AF_UNSPEC);  // disconnect
Socket remains open,You can still: sendto(...) to arbitrary addresses, and recvfrom(...) from new peer.

一句话总结就是,disconnect() breaks the association with the remote peer while close() destroys the channel itself — for TCP they are equivalent, for UDP they are fundamentally different.
```

## DefaultChannelConfig

```text
public NioSocketChannel(Channel parent, SocketChannel socket) {
    super(parent, socket);
    config = new NioSocketChannelConfig(this, socket.socket());     // NioSocketChannel在创建时,会同步创建NioSocketChannelConfig(默认使用AdaptiveRecvByteBufAllocator)
}

public DefaultChannelConfig(Channel channel) {
    this(channel, new AdaptiveRecvByteBufAllocator());             // AdaptiveRecvByteBufAllocator会在初始会使用比较小的byteBuf从socket读取数据,然后会基于上次实际从socket读取到的数据量,在下次使用大小更合适的byteBuf,如果上次实际读取量没有占满byteBuf的存储空间下次换更小的byteBuf,反之用更大的byteBuf
}

// RecvByteBufAllocator
```