# AbstractBootstrap

启动类(包含client/server2类)


AbstractBootstrap(递归泛型定义)
```text
public abstract class AbstractBootstrap<B extends AbstractBootstrap<B, C>, C extends Channel>
是 Java 中一个复杂的泛型递归定义,常用于构建流式 API或Builder模式,你可以在 Netty 框架的源码中看到类似的用法.
下面逐步解释含义

AbstractBootstrap 是一个抽象类
它有两个泛型参数：
    B：代表具体的 Bootstrap 类型,要求必须继承自 AbstractBootstrap<B, C>,也就是“自身类型的子类”,形成递归泛型定义.💯💯
    C：代表某种类型的 Channel,是 Netty 中的 IO 通道接口.

整体意义总结
AbstractBootstrap是一个抽象类,用于构建 bootstrap实例,支持链式调用,参数 B 是具体的子类,C 是通道类型,用于强类型约束.

-----------------------------------------------------------------
为什么使用递归泛型（B extends AbstractBootstrap<B, C>）?   💯💯💯
这是为了实现流式链式调用时返回具体子类类型,而不是父类.
public class ServerBootstrap extends AbstractBootstrap<ServerBootstrap, ServerChannel> {
    public ServerBootstrap option(...){
        // do something
        return this;
    }
}

如果不使用递归泛型,option() 方法返回的是 AbstractBootstrap 类型,那么你就无法在链式调用中使用 ServerBootstrap 的特有方法.
而现在使用递归泛型后：
public B option(...) {
    // ...
    return (B) this;
}
这样 ServerBootstrap.option() 返回的就是 ServerBootstrap,可以继续链式调用.
```





























