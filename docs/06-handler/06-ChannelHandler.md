# ChannelHandler相关



## ChannelHandler

```text
Handles an I/O event or intercepts an I/O operation, and forwards it to its next handler in its ChannelPipeline.


ChannelHandler itself does not provide many methods, but you usually have to implement one of its subtypes:
ChannelInboundHandler to handle inbound I/O events, and
ChannelOutboundHandler to handle outbound I/O operations.

Alternatively, the following adapter classes are provided for your convenience:
ChannelInboundHandlerAdapter to handle inbound I/O events,
ChannelOutboundHandlerAdapter to handle outbound I/O operations, and
ChannelDuplexHandler to handle both inbound and outbound events
```

### The context object(ChannelHandlerContext)

```text
The context object(ChannelHandlerContext)💯💯💯

A ChannelHandler is provided with a ChannelHandlerContext object. 
A ChannelHandler is supposed to interact with the ChannelPipeline it belongs to via a context object. 💯💯💯
Using the context object, the ChannelHandler can pass events upstream or downstream, modify the pipeline dynamically, or store the information (using AttributeKeys) which is specific to the handler.
```


###  State management

```text
A ChannelHandler often needs to store some stateful information. The simplest and recommended approach is to use member variables.
Because the handler instance has a state variable which is dedicated to one connection(💯), you have to create a new handler instance for each new channel to avoid a race condition.
```

example:
```text
public class DataServerHandler extends SimpleChannelInboundHandler<Message> {
  
  private boolean loggedIn;

  @Override
  public void channelRead0(ChannelHandlerContext ctx, Message message) {
      if (message instanceof LoginMessage) {
          authenticate((LoginMessage) message);
          loggedIn = true;
      } else (message instanceof GetDataMessage) {
          if (loggedIn) {
              ctx.writeAndFlush(fetchSecret((GetDataMessage) message));
          } else {
              fail();
          }
      }
  }
  ...
}

// Create a new handler instance per channel.
// See ChannelInitializer.initChannel(Channel).
public class DataServerInitializer extends ChannelInitializer<Channel> {
  @Override
  public void initChannel(Channel channel) {
      channel.pipeline().addLast("handler", new DataServerHandler());
  }
}
```

### Using AttributeKeys

```text
Although it's recommended to use member variables to store the state of a handler, for some reason you might not want to create many handler instances. 
In such a case, you can use AttributeKeys which is provided by ChannelHandlerContext.
```

example:
```text
@Sharable
public class DataServerHandler extends SimpleChannelInboundHandler<Message> {

  private final AttributeKey<Boolean> auth = AttributeKey.valueOf("auth");

  @Override
  public void channelRead(ChannelHandlerContext ctx, Message message) {
      Attribute<Boolean> attr = ctx.attr(auth);
      if (message instanceof LoginMessage) {
          authenticate((LoginMessage) o);
          attr.set(true);
      } else (message instanceof GetDataMessage) {
          if (Boolean.TRUE.equals(attr.get())) {
              ctx.writeAndFlush(fetchSecret((GetDataMessage) o));
          } else {
              fail();
          }
      }
  }
  ...
}


Now that the state of the handler is attached to the ChannelHandlerContext, you can add the same handler instance to different pipelines.

public class DataServerInitializer extends ChannelInitializer<Channel> {

  private static final DataServerHandler SHARED = new DataServerHandler();

  @Override
  public void initChannel(Channel channel) {
      channel.pipeline().addLast("handler", SHARED);
  }
}
```

### The @Sharable annotation

```text
In the example above which used an AttributeKey, you might have noticed the @Sharable annotation.
If a ChannelHandler is annotated with the @Sharable annotation, it means you can create an instance of the handler just once and add it to one or more ChannelPipelines multiple times without a race condition.
If this annotation is not specified, you have to create a new handler instance every time you add it to a pipeline because it has unshared state such as member variables.

This annotation is provided for documentation purpose, just like the JCIP annotations .(💯@Sharable注解是doc性质的)
```




## ChannelHandlerContext

```text
Enables a ChannelHandler to interact with its ChannelPipeline and other handlers.
Among other things a handler can notify the next ChannelHandler in the ChannelPipeline as well as modify the ChannelPipeline it belongs to dynamically.


🚀Notify
You can notify the closest handler in the same ChannelPipeline by calling one of the various methods provided here. Please refer to ChannelPipeline to understand how an event flows.

🚀Modifying a pipeline
You can get the ChannelPipeline your handler belongs to by calling pipeline(). A non-trivial application could insert, remove, or replace handlers in the pipeline dynamically at runtime.

🚀Retrieving for later use
You can keep the ChannelHandlerContext for later use, such as triggering an event outside the handler methods, even from a different thread. (ChannelHandlerContext可以取出来在外部线程使用💯)

🚀Storing stateful information
attr(AttributeKey) allow you to store and access stateful information that is related with a ChannelHandler/Channel and its context. (💯)
Please refer to ChannelHandler to learn various recommended ways to manage stateful information.


🚀A handler can have more than one ChannelHandlerContext
Please note that a ChannelHandler instance can be added to more than one ChannelPipeline. (💯💯💯)
It means a single ChannelHandler instance can have more than one ChannelHandlerContext and therefore the single instance can be invoked with different ChannelHandlerContexts if it is added to one or more ChannelPipelines more than once. 
Also note that a ChannelHandler that is supposed to be added to multiple ChannelPipelines should be marked as ChannelHandler.Sharable.
```


```text
public interface ChannelHandlerContext extends AttributeMap, ChannelInboundInvoker, ChannelOutboundInvoker {
    ChannelFuture bind(SocketAddress localAddress);
    
    ChannelFuture connect(SocketAddress remoteAddress);
    ChannelFuture disconnect();
    ChannelFuture close();
    
    ChannelOutboundInvoker read();
    ChannelFuture write(Object msg);
    
    ChannelFuture deregister();
}
```

## ChannelPipeline


```text
A list of ChannelHandlers which handles or intercepts inbound events and outbound operations of a Channel. 
ChannelPipeline implements an advanced form of the Intercepting Filter pattern to give a user full control over how an event is handled and how the ChannelHandlers in a pipeline interact with each other. (过滤器模式的实现)


🚀Creation of a pipeline
Each channel has its own pipeline and it is created automatically when a new channel is created.(💯💯💯) 
(pipeline与channel是一对一的关系,ChannelHandlerContext与channel也是一对一的关系.由于channelHandler可以被设置到多个pipeline,所以是一对多的关系)



🚀How an event flows in a pipeline
The following diagram describes how I/O events are processed by ChannelHandlers in a ChannelPipeline typically. 
An I/O event is handled by either a ChannelInboundHandler or a ChannelOutboundHandler and be forwarded to its closest handler by calling the event propagation methods defined in ChannelHandlerContext, such as ChannelHandlerContext.fireChannelRead(Object) and ChannelHandlerContext.write(Object).

An inbound event is handled by the inbound handlers in the bottom-up direction as shown on the left side of the diagram. 
An inbound handler usually handles the inbound data generated by the I/O thread on the bottom of the diagram. (💯)
The inbound data is often read from a remote peer via the actual input operation such as SocketChannel.read(ByteBuffer). If an inbound event goes beyond the top inbound handler, it is discarded silently, or logged if it needs your attention.(💯💯💯)

An outbound event is handled by the outbound handler in the top-down direction as shown on the right side of the diagram. 
An outbound handler usually generates or transforms the outbound traffic such as write requests. (💯)
If an outbound event goes beyond the bottom outbound handler, it is handled by an I/O thread associated with the Channel. (💯💯💯)
The I/O thread often performs the actual output operation such as SocketChannel.write(ByteBuffer).
```


```text
🚀Forwarding an event to the next handler

As you might noticed in the diagram shows, a handler has to invoke the event propagation methods in ChannelHandlerContext to forward an event to its next handler. (💯💯💯 过滤器模式中需要手动触发下个过滤器.类似于servlet,需要在Filter.doFilter方法中手动chain.doFilter(req, resp))


Inbound event propagation methods:                                   (inbound的起始来自于netty的selector捕获了socket的变化) (receive I/O events💯💯💯)
        ChannelHandlerContext.fireChannelRegistered()
        ChannelHandlerContext.fireChannelActive()
        ChannelHandlerContext.fireChannelRead(Object)
        ChannelHandlerContext.fireChannelReadComplete()
        ChannelHandlerContext.fireExceptionCaught(Throwable)
        ChannelHandlerContext.fireUserEventTriggered(Object)
        ChannelHandlerContext.fireChannelWritabilityChanged()
        ChannelHandlerContext.fireChannelInactive()
        ChannelHandlerContext.fireChannelUnregistered()

Outbound event propagation methods:                                    (outbound的起始来自于用户代码发起的I/O操作,比如bind/connect/write操作) (request I/O operations💯💯💯)
        ChannelHandlerContext.bind(SocketAddress, ChannelPromise)
        ChannelHandlerContext.connect(SocketAddress, SocketAddress, ChannelPromise)
        ChannelHandlerContext.write(Object, ChannelPromise)
        ChannelHandlerContext.flush()
        ChannelHandlerContext.read()
        ChannelHandlerContext.disconnect(ChannelPromise)
        ChannelHandlerContext.close(ChannelPromise)
        ChannelHandlerContext.deregister(ChannelPromise)
```

```text
🚀Building a pipeline

A user is supposed to have one or more ChannelHandlers in a pipeline to receive I/O events (e.g. read) and to request I/O operations (e.g. write and close). 

For example, a typical server will have the following handlers in each channel's pipeline, but your mileage may vary depending on the complexity and characteristics of the protocol and business logic:
Protocol Decoder - translates binary data (e.g. ByteBuf) into a Java object.
Protocol Encoder - translates a Java object into binary data.
Business Logic Handler - performs the actual business logic (e.g. database access).

and it could be represented as shown in the following example:
  static final EventExecutorGroup group = new DefaultEventExecutorGroup(16);
  ...
  ChannelPipeline pipeline = ch.pipeline();
 
  pipeline.addLast("decoder", new MyProtocolDecoder());
  pipeline.addLast("encoder", new MyProtocolEncoder());
 
  // Tell the pipeline to run MyBusinessLogicHandler's event handler methods in a different thread than an I/O thread so that the I/O thread is not blocked by a time-consuming task.
  // If your business logic is fully asynchronous or finished very quickly, you don't need to specify a group.
  pipeline.addLast(group, "handler", new MyBusinessLogicHandler()); (如果ChannelHandler是耗时比较久,那么需要将其在独立的EventExecutorGroup中执行,避免占用I/O线程💯💯💯)
  
Be aware that while using DefaultEventLoopGroup will offload the operation from the EventLoop it will still process tasks in a serial fashion per ChannelHandlerContext and so guarantee ordering. 
Due the ordering it may still become a bottle-neck. If ordering is not a requirement for your use-case you may want to consider using UnorderedThreadPoolEventExecutor to maximize the parallelism of the task execution.(💯💯💯)
```

```text
Thread safety

A ChannelHandler can be added or removed at any time because a ChannelPipeline is thread safe. 
For example, you can insert an encryption handler when sensitive information is about to be exchanged, and remove it after the exchange.
```



![netty-pipeline](../images/netty-pipeline.png)







