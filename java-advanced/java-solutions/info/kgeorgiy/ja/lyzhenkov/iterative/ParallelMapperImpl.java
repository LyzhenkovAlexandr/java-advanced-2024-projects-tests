package info.kgeorgiy.ja.lyzhenkov.iterative;

import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

public class ParallelMapperImpl implements ParallelMapper {

    private static final int SIZE_QUEUE_TASK = 10_000;
    private final QueueTasks<Task> queueTasks;
    private final List<Thread> poolThreads;

    public ParallelMapperImpl(final int threads) {
        this.queueTasks = new QueueTasks<>(SIZE_QUEUE_TASK);
        this.poolThreads = IntStream.range(0, threads).mapToObj(it -> new Thread(() -> {
            try {
                while (!Thread.interrupted()) {
                    queueTasks.getTask().run();
                }
            } catch (final InterruptedException ignored) {
            } finally {
                Thread.currentThread().interrupt();
            }
        })).peek(Thread::start).toList();
    }

    @Override
    public <T, R> List<R> map(Function<? super T, ? extends R> f, List<? extends T> items) throws InterruptedException {
        var answers = new AnswersList<R>(items.size());
        var tasks = IntStream.range(0, items.size()).<Task>mapToObj(i -> () ->
                answers.setAnswer(i, f.apply(items.get(i)))).toList();
        for (var task : tasks) {
            queueTasks.addTask(task);
        }
        return answers.getAnswers();
    }

    @Override
    public void close() {
        poolThreads.forEach(Thread::interrupt);
        for (var thread : poolThreads) {
            try {
                thread.join();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class AnswersList<E> {
        private final List<E> answers;
        private int count = 0;

        private AnswersList(final int size) {
            this.answers = new ArrayList<>(Collections.nCopies(size, null));
        }

        public void setAnswer(final int index, final E element) {
            answers.set(index, element);
            synchronized (this) {
                count++;
                notify();
            }
        }

        public synchronized List<E> getAnswers() throws InterruptedException {
            while (count != answers.size()) {
                wait();
            }
            return answers;
        }
    }
}
