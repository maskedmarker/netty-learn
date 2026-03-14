# ImmediateEventExecutor

```text
Executes Runnable objects in the caller's thread.  💯💯💯

If the execute(Runnable) is reentrant it will be queued until the original Runnable finishes execution.
All Throwable objects thrown from execute(Runnable) will be swallowed and logged. This is to ensure that all queued Runnable objects have the chance to be run.
```


```text
public final class ImmediateEventExecutor extends AbstractEventExecutor {

    // 全局唯一
    public static final ImmediateEventExecutor INSTANCE = new ImmediateEventExecutor();
    
    // 用来存储初始任务在执行过程中产生的子任务,这些子任务在初始任务完成后再执行
    private static final FastThreadLocal<Queue<Runnable>> DELAYED_RUNNABLES = new FastThreadLocal<Queue<Runnable>>() {
            @Override
            protected Queue<Runnable> initialValue() throws Exception {
                return new ArrayDeque<Runnable>();
            }
        };
    
    // 开始执行任务时,设置为true,任务都执行完后,设置为false    
    private static final FastThreadLocal<Boolean> RUNNING = new FastThreadLocal<Boolean>() {
            @Override
            protected Boolean initialValue() throws Exception {
                return false;
            }
        };    
        
    public void execute(Runnable command) {
        if (command == null) {
            throw new NullPointerException("command");
        }
        
        if (!RUNNING.get()) {
            RUNNING.set(true);         // 初始任务开始执行
            try {
                command.run();
            } catch (Throwable cause) {
                logger.info("Throwable caught while executing Runnable {}", command, cause);
            } finally {
                Queue<Runnable> delayedRunnables = DELAYED_RUNNABLES.get();
                Runnable runnable;
                
                // 初始任务完成后,再执行子任务command_N
                while ((runnable = delayedRunnables.poll()) != null) {
                    try {
                        runnable.run();
                    } catch (Throwable cause) {
                        logger.info("Throwable caught while executing Runnable {}", runnable, cause);
                    }
                }
                
                RUNNING.set(false);
            }
        } else {
            DELAYED_RUNNABLES.get().add(command);     // 初始command在run()方法中产生的子任务command_N,放入到DELAYED_RUNNABLES队列中
        }
    }
}
```


















