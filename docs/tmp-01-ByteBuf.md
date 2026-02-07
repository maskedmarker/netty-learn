# ByteBuf

## ByteBuf doc

```text
A random and sequential accessible sequence of zero or more bytes (octets). 
This interface provides an abstract view for one or more primitive byte arrays (byte[]) and NIO buffers.

Just like an ordinary primitive byte array, ByteBuf uses zero-based indexing.

ByteBuf provides two pointer variables to support sequential read and write operations - readerIndex for a read operation and writerIndex for a write operation respectively. 
The following diagram shows how a buffer is segmented into three areas by the two pointers:
       +-------------------+------------------+------------------+
       | discardable bytes |  readable bytes  |  writable bytes  |
       |                   |     (CONTENT)    |                  |
       +-------------------+------------------+------------------+
       |                   |                  |                  |
       0      <=      readerIndex   <=   writerIndex    <=    capacity
       
Readable bytes (the actual content) is where the actual data is stored. 
Writable bytes is a undefined space which needs to be filled.
Discardable bytes contains the bytes which were read already by a read operation.
```


## 