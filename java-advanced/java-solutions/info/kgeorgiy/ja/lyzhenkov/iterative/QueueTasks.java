package info.kgeorgiy.ja.lyzhenkov.iterative;

import java.util.ArrayDeque;
import java.util.Queue;

public class QueueTasks<T> {
    private final Queue<T> queue;
    private final int MAX_SIZE_QUEUE;
    private int size = 0;

    public QueueTasks(final int size) {
        this.queue = new ArrayDeque<>();
        this.MAX_SIZE_QUEUE = size;
    }

    public synchronized void addTask(final T task) throws InterruptedException {
        while (size == MAX_SIZE_QUEUE) {
            wait();
        }
        queue.add(task);
        size++;
        notifyAll();
    }

    public synchronized T getTask() throws InterruptedException {
        while (size == 0) {
            wait();
        }
        final T task = queue.poll();
        size--;
        notifyAll();
        return task;
    }
}
