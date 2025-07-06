# 主要类的结构关系

从接口的定义,来观察Channel/ChannelPipeline/ChannelHandlerContext/ChannelHandler的结构关系

```text
public interface Channel extends AttributeMap, ChannelOutboundInvoker, Comparable<Channel> {
    ChannelPipeline pipeline();
}

public interface ChannelPipeline extends ChannelInboundInvoker, ChannelOutboundInvoker, Iterable<Entry<String, ChannelHandler>> {
    Channel channel();
}

public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {
    Channel channel();
    ChannelHandler handler();
    ChannelPipeline pipeline();
}
```

从netty的具体实现,来观察Channel/ChannelPipeline/ChannelHandlerContext/ChannelHandler的结构关系
```text
public class DefaultChannelPipeline implements ChannelPipeline {
	// ChannelHandlerContext以双向链表的形式存在
    final HeadContext head;
    final TailContext tail;
    
    // ChannelPipeline所属的Channel
	private final Channel channel;
}

abstract class AbstractChannelHandlerContext implements ChannelHandlerContext, ResourceLeakHint {
	// 双向链表中当前节点(ChannelHandlerContext)的前后节点的引用
    volatile AbstractChannelHandlerContext next;
    volatile AbstractChannelHandlerContext prev;
    
    // ChannelHandlerContext双向链表所属的ChannelPipeline
	private final DefaultChannelPipeline pipeline;
	
	// 双向链表中当前节点(ChannelHandlerContext)的的ChannelHandler的状态
	private volatile int handlerState = INIT;
}		
final class DefaultChannelHandlerContext extends AbstractChannelHandlerContext {
    // 双向链表中当前节点(ChannelHandlerContext)的ChannelHandler
	private final ChannelHandler handler;
}
// AbstractChannelHandlerContext并不直接持有Channel,而是通过DefaultChannelPipeline间接持有Channel


public abstract class AbstractChannel extends DefaultAttributeMap implements Channel {
    private final Channel parent;
    private final ChannelId id;
    
    // Channel持有pipeline的引用
    private final DefaultChannelPipeline pipeline;
    
    private volatile SocketAddress localAddress;
    private volatile SocketAddress remoteAddress;
    private volatile EventLoop eventLoop;
}
```

## 为什么DefaultChannelPipeline中有多个ChannelHandlerContext
```text
ChannelPipeline使用类似于servlet的filter-chain的设计模式.
在servlet规范中,在同一个http请求中,servlet的filter-chain中所有filter使用相同的上下文.
而在netty中,在同一个请求中,ChannelPipeline中不同的ChannelHandler允许使用不同的上下文(ChannelHandlerContext)
DefaultChannelPipeline中不直接持有ChannelHandler,而是直接持有ChannelHandlerContext,再由ChannelHandlerContext直接持有ChannelHandler.如果要调用ChannelHandler的回调方法,则通过调用ChannelHandlerContext中与之对应的方法来间接完成


@Override
public ChannelHandlerContext fireChannelRegistered() {
    // 先找到ChannelHandlerContext
    invokeChannelRegistered(findContextInbound(MASK_CHANNEL_REGISTERED));
    return this;
}

static void invokeChannelRegistered(final AbstractChannelHandlerContext next) {
    EventExecutor executor = next.executor();
    if (executor.inEventLoop()) {
        // 调用ChannelHandlerContext上对应的方法
        next.invokeChannelRegistered();
    } else {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                next.invokeChannelRegistered();
            }
        });
    }
}

private void invokeChannelRegistered() {
    if (invokeHandler()) {
        try {
            // 最后将ChannelHandlerContext作为入参,调用ChannelHandler对应的方法
            final ChannelHandler handler = handler();
            final DefaultChannelPipeline.HeadContext headContext = pipeline.head;
            if (handler == headContext) {
                headContext.channelRegistered(this);
            } else if (handler instanceof ChannelInboundHandlerAdapter) {
                ((ChannelInboundHandlerAdapter) handler).channelRegistered(this);
            } else {
                ((ChannelInboundHandler) handler).channelRegistered(this);
            }
        } catch (Throwable t) {
            invokeExceptionCaught(t);
        }
    } else {
        fireChannelRegistered();
    }
}        
```

