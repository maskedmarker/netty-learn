# Bootstrap

client端的启动类
(为了简化描述,这里只讨论socket类型的Channel)

```text
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel> implements Cloneable {
    volatile EventLoopGroup group;
    private volatile ChannelFactory<? extends C> channelFactory;
    private volatile SocketAddress localAddress;                                                               // channel所对应的socket地址(因为Bootstrap保存的都是启动配置信息,所以在发生connect前,是不存在Channel对象的)

    private final Map<ChannelOption<?>, Object> options = new LinkedHashMap<ChannelOption<?>, Object>();       // 可以是socket参数
    private final Map<AttributeKey<?>, Object> attrs = new ConcurrentHashMap<AttributeKey<?>, Object>();       // 用户数据
    private volatile ChannelHandler handler;                                                                   // 为处理channel事件而配置的handler(因为支持动态为channelPipeline添加handler,所以启动时只配置一个handler就够了)
    private volatile ClassLoader extensionsClassLoader;
}


public class Bootstrap extends AbstractBootstrap<Bootstrap, Channel> {
    private final BootstrapConfig config = new BootstrapConfig(this);    // 想要访问启动配置数据,不能直接访问Bootstrap的属性,必须通过BootstrapConfig这个间接类
}

Bootstrap作为启动类,包含了启动必须的配置信息和启动方法.
```

```text
public B group(EventLoopGroup group) {     // 配置处理底层Channel事件的线程池(group->线程池, event-loop->线程不断地循环来处理事件)
    this.group = group;
    return self();
}

public B channel(Class<? extends C> channelClass) {
    return channelFactory(new ReflectiveChannelFactory<C>(channelClass));        // 配置创建底层Channel对象的工厂类
}

public B handler(ChannelHandler handler) {
    this.handler = ObjectUtil.checkNotNull(handler, "handler");                  // 配置处理Channel对象事件的handler
    return self();
}


public Bootstrap validate() {
    if (group == null) {
        throw new IllegalStateException("group not set");                        // ②如果没有配置处理事件的线程池,启动就是无意的,必须启动失败
    }
    if (channelFactory == null) {
        throw new IllegalStateException("channel or channelFactory not set");    // ①如果没有配置channel工厂类,就无法基于用户的connect入参信息构建并监听channel的事件,启动就是无意的,必须启动失败
    }
    if (config.handler() == null) {
        throw new IllegalStateException("handler not set");                      // ③如果没有配置处理事件的handler,事件得不到处理,启动就是无意的,必须启动失败
    }
    return this;
}
```


```text
public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
    return doResolveAndConnect(remoteAddress, localAddress);
}

private ChannelFuture doResolveAndConnect(final SocketAddress remoteAddress, final SocketAddress localAddress) {
    final ChannelFuture regFuture = initAndRegister();
    final Channel channel = regFuture.channel();

    if (regFuture.isDone()) {
        if (!regFuture.isSuccess()) {
            return regFuture;
        }
        return doResolveAndConnect0(channel, remoteAddress, localAddress, channel.newPromise());
    } else {
        // Registration future is almost always fulfilled already, but just in case it's not.
        final PendingRegistrationPromise promise = new PendingRegistrationPromise(channel);
        regFuture.addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                // Directly obtain the cause and do a null check so we only need one volatile read in case of a
                // failure.
                Throwable cause = future.cause();
                if (cause != null) {
                    // Registration on the EventLoop failed so fail the ChannelPromise directly to not cause an
                    // IllegalStateException once we try to access the EventLoop of the Channel.
                    promise.setFailure(cause);
                } else {
                    // Registration was successful, so set the correct executor to use.
                    // See https://github.com/netty/netty/issues/2586
                    promise.registered();
                    doResolveAndConnect0(channel, remoteAddress, localAddress, promise);
                }
            }
        });
        return promise;
    }
}
    
final ChannelFuture initAndRegister() {
    Channel channel = null;
    try {
        channel = channelFactory.newChannel();                                                             // channel对象在构造时,其内部会同时构造一个与之匹配的channelPipeline对象
        init(channel);                                                                                     // 此时的channel对象还未初始化(没有与socketChannel关联)
    } catch (Throwable t) {
        // ...
    }

    ChannelFuture regFuture = config().group().register(channel);                                          // 向线程池提交一个注册channel的异步任务 
    if (regFuture.cause() != null) {
        if (channel.isRegistered()) {
            channel.close();
        } else {
            channel.unsafe().closeForcibly();
        }
    }

    return regFuture;
}


void init(Channel channel) {
    ChannelPipeline p = channel.pipeline();
    p.addLast(config.handler());                                                                               // 💯💯💯启动配置中的handler被添加到了channel的pipeline中(该handler可以根据需要在回调方法中往channelPipeline中动态添加其他handler)

    setChannelOptions(channel, newOptionsArray(), logger);                                                     // 💯将启动配置复制到ChannelConfig,供后续创建socket时使用
    setAttributes(channel, newAttributesArray());                                                              // 💯将启动配置复制到channel的attributes中
    
    Collection<ChannelInitializerExtension> extensions = getInitializerExtensions();                           // ChannelInitializerExtension暂时框架没有用到
    if (!extensions.isEmpty()) {
        for (ChannelInitializerExtension extension : extensions) {
            try {
                extension.postInitializeClientChannel(channel);
            } catch (Exception e) {
                logger.warn("Exception thrown from postInitializeClientChannel", e);
            }
        }
    }
}
```





























