# Future

## Promise

```text
Special Future which is writable.  
(可以手动设置 成功/失败/不可取消, 可以添加Listener)

备注: java8才有支持手动设置的CompletableFuture,晚于netty项目的启动时间.
```


## ChannelFuture

```text
The result of an asynchronous Channel I/O operation.
All I/O operations in Netty are asynchronous. 
It means any I/O calls will return immediately with no guarantee that the requested I/O operation has been completed at the end of the call. 
Instead, you will be returned with a ChannelFuture instance which gives you the information about the result or status of the I/O operation.

A ChannelFuture is either uncompleted or completed. 
When an I/O operation begins, a new future object is created.
The new future is uncompleted initially - it is neither succeeded, failed, nor cancelled because the I/O operation is not finished yet. 
If the I/O operation is finished either successfully, with failure, or by cancellation, the future is marked as completed with more specific information, such as the cause of the failure. 
Please note that even failure and cancellation belong to the completed state.

                                       +---------------------------+
                                       | Completed successfully    |
                                       +---------------------------+
                                  +---->      isDone() = true      |
  +--------------------------+    |    |   isSuccess() = true      |
  |        Uncompleted       |    |    +===========================+
  +--------------------------+    |    | Completed with failure    |
  |      isDone() = false    |    |    +---------------------------+
  |   isSuccess() = false    |----+---->      isDone() = true      |
  | isCancelled() = false    |    |    |       cause() = non-null  |
  |       cause() = null     |    |    +===========================+
  +--------------------------+    |    | Completed by cancellation |
                                  |    +---------------------------+
                                  +---->      isDone() = true      |
                                       | isCancelled() = true      |
                                       +---------------------------+
                                       

Various methods are provided to let you check if the I/O operation has been completed, wait for the completion, and retrieve the result of the I/O operation. 
It also allows you to add ChannelFutureListeners so you can get notified when the I/O operation is completed. 
It is recommended to prefer addListener(GenericFutureListener) to await() wherever possible to get notified when an I/O operation is done and to do any follow-up tasks.
addListener(GenericFutureListener) is non-blocking. By contrast, await() is a blocking operation. Once called, the caller thread blocks until the operation is done.

Do not call await() inside ChannelHandler.
The event handler methods in ChannelHandler are usually called by an I/O thread. 
If await() is called by an event handler method, which is called by the I/O thread, the I/O operation it is waiting for might never complete because await() can block the I/O operation it is waiting for, which is a dead lock.   


Do not confuse I/O timeout and await timeout  💯💯💯
The timeout value you specify with await(long), await(long, TimeUnit), awaitUninterruptibly(long), or awaitUninterruptibly(long, TimeUnit) are not related with I/O timeout at all. 
If an I/O operation times out, the future will be marked as 'completed with failure,' as depicted in the diagram above. 
For example, connect timeout should be configured via a transport-specific option:   
bootstrap.option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000);                      
```




























