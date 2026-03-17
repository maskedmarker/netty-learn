# NioServerSocketChannel


```text
public class NioServerSocketChannel extends AbstractNioMessageChannel implements io.netty.channel.socket.ServerSocketChannel {
}
```

## AbstractNioMessageChannel

AbstractNioMessageChannel用于server端(不支持发起连接connect)/或者无需发起连接的平等对端.
AbstractNioMessageChannel支持读取message. message是一个更加宽泛的概念,不止是字节数据(比如读取到的是一个accept到的socket-channel).

(备注: NioDatagramChannel也继承自AbstractNioMessageChannel,因为无需发起连接connect)


```text
// AbstractNioChannel base class for Channels that operate on messages(💯💯💯).
public abstract class AbstractNioMessageChannel extends AbstractNioChannel {
    
    private final class NioMessageUnsafe extends AbstractNioUnsafe {
        public void read() {
            // ...
            int localRead = doReadMessages(readBuf);
            // ...
        }
    }
}
```

## NioServerSocketChannel.doReadMessages

```text
io.netty.channel.socket.nio.NioServerSocketChannel.doReadMessages
protected int doReadMessages(List<Object> buf) throws Exception {
    SocketChannel ch = SocketUtils.accept(javaChannel());

    try {
        if (ch != null) {
            buf.add(new NioSocketChannel(this, ch));                                // 💯💯💯新accept的NioSocketChannel被当作读取到的message,这也是为什么NioEventLoop中将OP_READ/OP_ACCEPT都归为read操作
            return 1;
        }
    } catch (Throwable t) {
        logger.warn("Failed to create a new channel from an accepted socket.", t);

        try {
            ch.close();
        } catch (Throwable t2) {
            logger.warn("Failed to close a socket.", t2);
        }
    }

    return 0;
}
```