package org.webbeans.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/**
 * Runs tasks one at a time, in submission order, on a shared backend executor — a logical thread per session
 * without a real thread per session. Tasks submitted while a task runs execute after it.
 */
public final class SerialExecutor implements Executor {

    private final Executor backend;
    private final Deque<Runnable> queue = new ArrayDeque<Runnable>();
    private boolean running;
    private volatile Thread currentThread;

    public SerialExecutor(Executor backend) {
        this.backend = backend;
    }

    @Override
    public void execute(Runnable task) {
        synchronized (queue) {
            queue.addLast(task);
            if (running) {
                return;
            }
            running = true;
        }
        backend.execute(new Runnable() {
            @Override
            public void run() {
                drain();
            }
        });
    }

    private void drain() {
        currentThread = Thread.currentThread();
        try {
            while (true) {
                Runnable task;
                synchronized (queue) {
                    task = queue.pollFirst();
                    if (task == null) {
                        running = false;
                        return;
                    }
                }
                try {
                    task.run();
                } catch (Throwable t) {
                    java.util.logging.Logger.getLogger(SerialExecutor.class.getName()).log(java.util.logging.Level.SEVERE, "Task failed", t);
                }
            }
        } finally {
            currentThread = null;
        }
    }

    /** Whether the calling thread is currently running this executor's tasks. */
    public boolean isCurrentThread() {
        return currentThread == Thread.currentThread();
    }

    public int pending() {
        synchronized (queue) {
            return queue.size();
        }
    }
}
