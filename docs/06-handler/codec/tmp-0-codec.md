# handler

```text
netty自带的所谓"粘包/拆包"解决方案

定长法：   FixedLengthFrameDecoder
分隔符法： DelimiterBasedFrameDecoder(如 LineBasedFrameDecoder 按换行符)
长度域法： LengthFieldBasedFrameDecoder(最通用,如消息头存长度,后面跟内容)
```

```text
简单常用的

StringDecoder/StringEncoder (ByteBuf <-> 字符串)
ObjectDecoder/ObjectEncoder (ByteBuf <-> java对象)
```

```text

ProtobufDecoder/ProtobufEncoder
```