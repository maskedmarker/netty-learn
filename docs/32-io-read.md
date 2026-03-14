# example-explain-i/o


## read

```text
io.netty.channel.DefaultChannelPipeline.HeadContext.read
    io.netty.channel.AbstractChannel.AbstractUnsafe.beginRead
        io.netty.channel.nio.AbstractNioChannel.doBeginRead
        
        

        
io.netty.channel.nio.AbstractNioChannel.doBeginRead
    protected void doBeginRead() throws Exception {
        // Channel.read() or ChannelHandlerContext.read() was called
        final SelectionKey selectionKey = this.selectionKey;
        if (!selectionKey.isValid()) {
            return;
        }

        readPending = true;

        final int interestOps = selectionKey.interestOps();
        if ((interestOps & readInterestOp) == 0) {
            selectionKey.interestOps(interestOps | readInterestOp);
        }
    }        
```






















