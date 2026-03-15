# HttpObjectDecoder

需要先理解http报文要求,再看代码.

```text
public abstract class HttpObjectDecoder extends ByteToMessageDecoder {

// 解析http响应也需要维护decoder的状态
private State currentState = State.SKIP_CONTROL_CHARS;
}
```