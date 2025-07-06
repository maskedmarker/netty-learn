# unix网络编程

学习unix网络编程是为了更好理解netty.

在 Unix 网络异步编程中，网络 I/O 事件通常分为以下几类，这些分类是异步 I/O 多路复用机制（如 select、poll、epoll、kqueue 等）的核心关注点：
```text
1. 可读事件（Readable Events）
   触发条件：当套接字（socket）的接收缓冲区中有数据可读时触发。

典型场景：
    TCP 套接字收到新数据（客户端发送数据或连接建立）。
    UDP 套接字收到数据报。
    监听套接字（listening socket）有新连接到达（accept 不会阻塞）。
    对端关闭连接（read 返回 0，触发可读事件）。

相关系统调用：read, recv, accept。


2. 可写事件（Writable Events）
   触发条件：当套接字的发送缓冲区有空间可写入时触发。

典型场景：
    TCP 套接字的发送缓冲区未满，可以写入数据（非阻塞 write 或 send 不会阻塞）。
    非阻塞连接（如 connect 正在进行）完成时（需要通过可写事件判断连接是否成功）。
    注意：可写事件通常持续触发（除非缓冲区满），因此通常在需要发送数据时才监听，避免无意义的 CPU 占用。
相关系统调用：write, send, connect（非阻塞模式下）。


3. 异常事件（Error/Exception Events）
   触发条件：当套接字发生错误或异常时触发。

典型场景：
    TCP 套接字收到 RST 复位（如对端异常关闭）。
    非阻塞 connect 失败（需要通过错误事件检测）。
    其他协议相关的错误（如带外数据错误）。
    处理方式：通过 getsockopt 读取 SO_ERROR 获取具体错误码。

注意：某些系统（如 Linux 的 epoll）会将错误事件与可读/可写事件合并通知。


4. 带外数据事件（Out-of-Band Data, OOB）
   触发条件：当套接字收到带外数据（如 TCP 的紧急数据）时触发。

典型场景：极少使用，通常与可读事件合并处理。

相关系统调用：recv 使用 MSG_OOB 标志。

5. 不同 I/O 多路复用机制的实现差异
    select/poll：显式区分可读（POLLIN）、可写（POLLOUT）、异常（POLLERR/POLLHUP）和带外数据（POLLPRI）。
    epoll：通过 epoll_event.events 字段组合 EPOLLIN、EPOLLOUT、EPOLLERR、EPOLLHUP 等。
    kqueue：使用 EVFILT_READ（读）、EVFILT_WRITE（写）、EVFILT_EXCEPT（异常）等过滤器。

6. 实际编程中的注意事项
连接成功判断：非阻塞 connect 需要通过可写事件 + 检查 SO_ERROR 确认是否成功。
边缘触发（ET）与水平触发（LT）：
    边缘触发（如 EPOLLET）需一次性处理完所有数据，避免遗漏。
    水平触发会持续通知，直到条件不再满足。
事件合并：某些机制（如 epoll）可能将错误事件与可读/可写事件一起返回，需主动检查错误。

通过监听这些事件，程序可以高效地管理大量并发连接，避免阻塞等待 I/O。
```


linux是怎么实现可写事件的?

在 Linux 中，可写事件（Writable Events） 的实现依赖于内核的 I/O 多路复用机制（如 epoll、select、poll），其核心原理是监控套接字发送缓冲区的状态。以下是 Linux 实现可写事件的关键细节：
```text
1. 可写事件的触发条件
当以下条件满足时，内核会触发可写事件：
    发送缓冲区有可用空间：TCP/UDP 套接字的发送缓冲区未满，可以写入数据（非阻塞 write/send 调用将成功或部分成功）。
    非阻塞连接完成：对非阻塞的 connect() 调用，连接建立成功后，套接字会变为可写（需结合 SO_ERROR 检查是否成功）。
    错误状态：如果套接字发生错误（如连接被重置），可写事件也会被触发（需通过 getsockopt(SO_ERROR) 检查错误码）。
    
2. 内核的实现机制
Linux 通过以下组件实现可写事件的通知：

（1）套接字发送缓冲区
每个套接字在内核中维护一个发送缓冲区（sk_write_queue），当缓冲区剩余空间 >= 低水位标记（SO_SNDLOWAT，默认 1 字节）时，可写事件被触发。

当应用层写入数据时，内核将数据拷贝到发送缓冲区；当数据被网络层发送后，缓冲区空间释放，触发可写事件。

（2）I/O 多路复用机制
epoll：通过 EPOLLOUT 事件通知可写状态。
select/poll：通过 POLLOUT 或 FD_ISSET(fd, &writefds) 检测可写性。

（3）内核回调与唤醒
当套接字从“不可写”变为“可写”时，内核通过回调机制（如 sk->sk_data_ready）通知等待的 I/O 多路复用模块。
对于 epoll，内核将可写事件添加到就绪队列（eventpoll->rdllist），并唤醒等待的进程。   

3. 边缘触发（ET）与水平触发（LT）的区别
水平触发（LT，默认模式）：
    只要发送缓冲区有空间，持续触发可写事件。
    可能导致频繁唤醒（如果应用不发送数据，但缓冲区一直未满）。
边缘触发（ET）：
    仅在套接字从“不可写”变为“可写”时触发一次。
    需一次性处理完所有可写数据（或直到 EAGAIN），否则可能丢失事件。
    
4. 实际编程中的注意事项

（1）避免无意义可写事件
可写事件默认持续触发（LT 模式），因此仅在需要发送数据时监听 EPOLLOUT，发送完成后移除监听，否则会导致 busy-loop。
// 示例：发送数据时动态监听可写事件
if (write(sockfd, buf, len) == -1 && errno == EAGAIN) {
    // 发送缓冲区满，开始监听可写事件
    epoll_ctl(epfd, EPOLL_CTL_MOD, sockfd, &event);
}

（2）非阻塞 connect 的处理
非阻塞 connect() 会返回 EINPROGRESS，需通过可写事件判断连接是否成功：
int err;
socklen_t len = sizeof(err);
getsockopt(sockfd, SOL_SOCKET, SO_ERROR, &err, &len);
if (err == 0) { /* 连接成功 */ }

（3）错误处理
可写事件触发时，可能伴随错误（如 EPOLLERR），需检查套接字错误状态：
if (events[i].events & EPOLLERR) {
    getsockopt(sockfd, SOL_SOCKET, SO_ERROR, &err, &len);
}


5. 性能优化
    EPOLLET + 非阻塞 I/O：边缘触发模式减少事件通知次数，但需确保一次性处理完数据。
    发送缓冲区调优：通过 setsockopt 调整 SO_SNDBUF 大小，平衡吞吐量和延迟。

总结
Linux 的可写事件本质是内核监控套接字发送缓冲区的状态变化，通过 I/O 多路复用机制通知用户态程序。正确使用需结合非阻塞 I/O、边缘触发/水平触发模式以及动态事件注册，以实现高效网络编程。     
```



