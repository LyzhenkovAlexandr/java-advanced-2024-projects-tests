package info.kgeorgiy.ja.lyzhenkov.iterative;

import info.kgeorgiy.java.advanced.iterative.NewListIP;
import info.kgeorgiy.java.advanced.mapper.ParallelMapper;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class IterativeParallelism implements NewListIP {

    private final ParallelMapper mapper;

    public IterativeParallelism(final ParallelMapper mapper) {
        this.mapper = mapper;
    }

    public IterativeParallelism() {
        this.mapper = null;
    }

    /**
     * Executes a computation across multiple threads on a list of values, and combines the results using a combiner function.
     * If the {@code mapper} is present, then {@code threads} are not created or used,
     * and all work is delegated to the {@link #mapper}.
     *
     * @param threads  the number of threads to be used for parallel computation.
     * @param values   the list of values to be processed in parallel.
     * @param executor a function that performs the computation on a stream of elements from the input list.
     * @param combiner a function that combines the results produced by each computation.
     * @param <T>      the type of elements in the input list.
     * @param <R>      the type of the result produced by the combiner function.
     * @return the combined result of the parallel computations.
     * @throws InterruptedException     along with suppressed interrupts.
     *                                  If any thread is interrupted while waiting for completion,
     *                                  it is suppressed with {@link Throwable#addSuppressed(Throwable)}.
     *                                  The mapper also throws an exception.
     * @throws IllegalArgumentException if the number of threads is less than 1.
     */
    private <T, R> R calcListOnThreads(
            int threads,
            List<? extends T> values,
            Function<Stream<? extends T>, ? extends R> executor,
            Function<Stream<R>, ? extends R> combiner
    ) throws InterruptedException {
        if (threads < 1) {
            throw new IllegalArgumentException("The number of threads must be at least 1, but received: " + threads);
        }
        var countThreads = Math.min(threads, values.size());
        var step = values.size() / countThreads;
        var rest = values.size() % countThreads;
        var subList = IntStream.range(0, countThreads).mapToObj(it -> {
            var left = step * it + Math.min(it, rest);
            var right = step * it + step + Math.min(it + 1, rest);

            return values.subList(left, right).stream();
        }).collect(Collectors.toList());

        if (Objects.nonNull(this.mapper)) {
            final List<R> result = mapper.map(executor, subList);
            return combiner.apply(result.stream());
        }

        final List<R> result = new ArrayList<>(Collections.nCopies(countThreads, null));
        var poolThreads = IntStream.range(0, countThreads)
                .mapToObj(it -> new Thread(() -> result.set(it, executor.apply(subList.get(it)))))
                .peek(Thread::start).toList();
        joinThreads(poolThreads);
        return combiner.apply(result.stream());
    }

    private static void joinThreads(final List<Thread> poolThreads) throws InterruptedException {
        InterruptedException exceptions = null;
        for (final Thread thread : poolThreads) {
            try {
                thread.join();
            } catch (final InterruptedException e) {
                if (Objects.nonNull(exceptions)) {
                    exceptions.addSuppressed(e);
                } else {
                    exceptions = e;
                }
            }
        }
        if (Objects.nonNull(exceptions)) {
            throw exceptions;
        }
    }

    private static <T> List<T> getElementsWithStep(final List<? extends T> values, final int step) {
        var res = new ArrayList<T>();
        for (int i = 0; i < values.size(); i += step) {
            res.add(values.get(i));
        }
        return res;
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step)
            throws InterruptedException {
        return maximum(threads, getElementsWithStep(values, step), comparator);
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator, int step)
            throws InterruptedException {
        return minimum(threads, getElementsWithStep(values, step), comparator);
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return all(threads, getElementsWithStep(values, step), predicate);
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return any(threads, getElementsWithStep(values, step), predicate);
    }

    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return count(threads, getElementsWithStep(values, step), predicate);
    }

    @Override
    public <T> T maximum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        return calcListOnThreads(threads,
                values,
                stream -> stream.max(comparator).orElseThrow(NoSuchElementException::new),
                stream -> stream.reduce(BinaryOperator.maxBy(comparator)).orElseThrow(NoSuchElementException::new));
    }

    @Override
    public <T> T minimum(int threads, List<? extends T> values, Comparator<? super T> comparator)
            throws InterruptedException {
        return maximum(threads, values, comparator.reversed());
    }

    @Override
    public <T> boolean all(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return count(threads, values, predicate) == values.size();
    }

    @Override
    public <T> boolean any(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return count(threads, values, predicate) > 0;
    }

    @Override
    public <T> int count(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return calcListOnThreads(threads,
                values,
                stream -> (int) stream.filter(predicate).count(),
                stream -> stream.reduce(Integer::sum).orElse(0));
    }

    @Override
    public String join(int threads, List<?> values, int step) throws InterruptedException {
        return join(threads, getElementsWithStep(values, step));
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate, int step)
            throws InterruptedException {
        return filter(threads, getElementsWithStep(values, step), predicate);
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f, int step)
            throws InterruptedException {
        return map(threads, getElementsWithStep(values, step), f);
    }

    @Override
    public String join(int threads, List<?> values) throws InterruptedException {
        return calcListOnThreads(threads,
                values,
                stream -> stream.map(Object::toString).collect(Collectors.joining()),
                stream -> stream.collect(Collectors.joining()));
    }

    @Override
    public <T> List<T> filter(int threads, List<? extends T> values, Predicate<? super T> predicate)
            throws InterruptedException {
        return mapOrFilter(threads, values, stream -> stream.filter(predicate).collect(Collectors.toList()));
    }

    @Override
    public <T, U> List<U> map(int threads, List<? extends T> values, Function<? super T, ? extends U> f)
            throws InterruptedException {
        return mapOrFilter(threads, values, stream -> stream.map(f).collect(Collectors.toList()));
    }

    private <T, R> List<R> mapOrFilter(
            int threads,
            List<? extends T> values,
            Function<Stream<? extends T>, List<R>> executor
    ) throws InterruptedException {
        return calcListOnThreads(threads, values, executor,
                stream -> stream.flatMap(Collection::stream).collect(Collectors.toList()));
    }
}
