# NioSocketChannel


```text
public class NioSocketChannel extends AbstractNioByteChannel implements io.netty.channel.socket.SocketChannel {
}
```

## AbstractNioByteChannel

AbstractNioByteChannel就是用于client端(需要发起连接connect).
而且AbstractNioByteChannel只支持读取字节数据,无法读取非字节信息(比如无法读取到一个新建立的连接,因为没必要,connect接口已经包含了该含义)


```text
// AbstractNioChannel base class for Channels that operate on bytes(💯💯💯).
public abstract class AbstractNioByteChannel extends AbstractNioChannel {

    protected class NioByteUnsafe extends AbstractNioUnsafe {
            public final void read() {
                // ...
                allocHandle.lastBytesRead(doReadBytes(byteBuf)); // 读取字节数据
                // ...
            }
    }
}
```

## NioSocketChannel.doReadBytes

```text
io.netty.channel.socket.nio.NioSocketChannel.doReadBytes
protected int doReadBytes(ByteBuf byteBuf) throws Exception {
    final RecvByteBufAllocator.Handle allocHandle = unsafe().recvBufAllocHandle();
    allocHandle.attemptedBytesRead(byteBuf.writableBytes());
    return byteBuf.writeBytes(javaChannel(), allocHandle.attemptedBytesRead());         // 从client-socket读取数据
}
```

```text

```