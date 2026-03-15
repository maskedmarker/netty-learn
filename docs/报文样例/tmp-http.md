# http报文

## Chunked transfer encoding 分块传输编码

在 HTTP/1.1 中,当服务器不确定要发送的数据总长度(例如动态生成内容),或者想要尽早开始发送数据时,会使用“分块传输编码”.这时,完整的响应报文会被拆分成多个块(Chunk)发送给客户端.

在语法上允许分块传输编码用于http请求,但是主要用户http响应.
很多 Web 服务器在处理请求时,会严格依赖 Content-Length 头部来分配缓冲区或判断请求结束.如果收到带有 Transfer-Encoding: chunked 的请求,可能会直接返回 411 Length Required 或 501 Not Implemented,甚至直接丢弃连接.


完整响应报文示例
```text
HTTP/1.1 200 OK
Content-Type: text/plain
Transfer-Encoding: chunked

1e
This is the first chunk.
1a
This is the second one.
0
```
响应报文解析
```text
HTTP/1.1 200 OK
Content-Type: text/plain
Transfer-Encoding: chunked                               // Transfer-Encoding: chunked 头部告诉客户端,接下来的数据不是一次性发送的,而是分块发送的.此时通常不会使用 Content-Length 头部.
                                                         // 头部与正文之间必须有一个空行(CRLF)
1e                                                       // 第一块.这是十六进制数字,表示该块数据的字节大小(30 字节).它独占一行,后面紧跟着 CRLF
This is the first chunk.                                 // 这里是实际的 30 字节数据内容.数据结束后紧跟着 CRLF
1a                                                       // 第二块
This is the second one.
0                                                        // 结束块. 当发送一个长度为 0 的块时,表示所有数据发送完毕. 之后通常还要紧跟 CRLF 表示结束.在某些实现中,结束块后面还可以跟一个可选的尾部(Trailer)头部.
```