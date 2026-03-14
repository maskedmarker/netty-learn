# GlobalEventExecutor


```text
GlobalEventExecutor 是 Netty 中一个非常特殊的 EventExecutor,
单例模式：整个 JVM 中只有一个实例
自动管理：它是一个单线程的EventExecutor,当没有任务时会自动终止线程,有任务时自动创建线程执行任务
全局共享：常用于定时任务,Promise的回调等不需要专用线程的场景

这种"按需启动、空闲终止"的特性,使得它既是全局共享的,又不会浪费线程资源。
```



```text
public final class GlobalEventExecutor extends AbstractScheduledEventExecutor {

public static final GlobalEventExecutor INSTANCE = new GlobalEventExecutor();


Queue<ScheduledFutureTask<?>> scheduledTaskQueue;                                   // (父类中定义的PriorityQueue) 定时类任务
final BlockingQueue<Runnable> taskQueue = new LinkedBlockingQueue<Runnable>();      // 非定时任务

volatile Thread thread;                                                             // 线程池只有一个线程(空闲时会被销毁,有新任务时重新创建)
private final TaskRunner taskRunner = new TaskRunner();                             // 工作线程的执行逻辑定义在该对象中

final ScheduledFutureTask<Void> quietPeriodTask = ...;                              // noop任务,用来判断任务队列中是否只剩下noop任务

}
```

## 提交任务

```text
public void execute(Runnable task) {
    if (task == null) {
        throw new NullPointerException("task");
    }

    addTask(task);                  // 添加任务
    if (!inEventLoop()) { 
        startThread();              // 确保有工作线程在运行
    }
}

private void startThread() {
    if (started.compareAndSet(false, true)) {
        Thread t = threadFactory.newThread(taskRunner);
        thread = t;
        t.start();
    }
}
```


## 工作线程的主逻辑

```text
final class TaskRunner implements Runnable {
    @Override
    public void run() {
        for (;;) {
            Runnable task = takeTask();
            if (task != null) {
                try {
                    task.run();
                } catch (Throwable t) {
                    logger.warn("Unexpected exception from the global event executor: ", t);
                }

                if (task != quietPeriodTask) {
                    continue;
                }
            }
            
            // 此时没有可执行的任务,准备自然结束当前工作线程
            
            Queue<ScheduledFutureTask<?>> scheduledTaskQueue = GlobalEventExecutor.this.scheduledTaskQueue;
            // Terminate if there is no task in the queue (except the noop task).
            if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                // Mark the current thread as stopped.
                // The following CAS must always success and must be uncontended, because only one thread should be running at the same time.
                boolean stopped = started.compareAndSet(true, false);
                assert stopped;

                // Check if there are pending entries added by execute() or schedule*() while we do CAS above.
                if (taskQueue.isEmpty() && (scheduledTaskQueue == null || scheduledTaskQueue.size() == 1)) {
                    // A) No new task was added and thus there's nothing to handle
                    //    -> safe to terminate because there's nothing left to do
                    // B) A new thread started and handled all the new tasks.
                    //    -> safe to terminate the new thread will take care the rest
                    break;
                }

                // There are pending tasks added again.
                if (!started.compareAndSet(false, true)) {
                    // startThread() started a new thread and set 'started' to true.
                    // -> terminate this thread so that the new thread reads from taskQueue exclusively.
                    break;
                }

                // New tasks were added, but this worker was faster to set 'started' to true.
                // i.e. a new worker thread was not started by startThread().
                // -> keep this thread alive to handle the newly added entries.
            }
        }
    }
}
```

```text
优先执行普通的任务,劣后执行定时任务.

Runnable takeTask() {
    BlockingQueue<Runnable> taskQueue = this.taskQueue;
    for (;;) {
    
        ScheduledFutureTask<?> scheduledTask = peekScheduledTask();
        if (scheduledTask == null) {
            Runnable task = null;
            try {
                task = taskQueue.take();                                           // 如果没有定时任务,再阻塞式的取普通任务(如果没有普通任务,则会被阻塞直到有新的普通任务)
            } catch (InterruptedException e) {
                // Ignore
            }
            return task;
        } else {
            long delayNanos = scheduledTask.delayNanos();
            Runnable task;
            if (delayNanos > 0) {
                try {
                    task = taskQueue.poll(delayNanos, TimeUnit.NANOSECONDS);       // 定时任务还不到触发时间,在这段时间内看是否有普通任务
                } catch (InterruptedException e) {
                    // Waken up.
                    return null;
                }
            } else {
                task = taskQueue.poll();                                           // 虽然定时任务可以触发了,但优先挑选普通任务
            }

            if (task == null) {
                fetchFromScheduledTaskQueue();
                task = taskQueue.poll();                                           // 如果没有普通任务,就选择可以触发的定时任务
            }

            if (task != null) {
                return task;
            }
        }
    }
}
```

### ScheduledFutureTask

```text
public void run() {
    assert executor().inEventLoop();
    try {
        if (periodNanos == 0) {                             // 非重复性任务
            if (setUncancellableInternal()) {               // 任务执行时,设置为不能取消
                V result = task.call();
                setSuccessInternal(result);
            }
        } else {                                            // 可重复执行的任务
            // check if is done as it may was cancelled
            if (!isCancelled()) {
                task.call();
                
                if (!executor().isShutdown()) {
                    long p = periodNanos;
                    if (p > 0) {
                        deadlineNanos += p;
                    } else {
                        deadlineNanos = nanoTime() - p;
                    }
                    if (!isCancelled()) {
                        Queue<ScheduledFutureTask<?>> scheduledTaskQueue = ((AbstractScheduledEventExecutor) executor()).scheduledTaskQueue;
                        assert scheduledTaskQueue != null;
                        scheduledTaskQueue.add(this);            // 可重复执行的任务在本次执行后,重新放到任务队列中(如果执行中发生异常,就不再放到任务队列中,后续无法再被执行)
                    }
                }
            }
        }
    } catch (Throwable cause) {
        setFailureInternal(cause);
    }
}
```





















