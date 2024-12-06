package info.kgeorgiy.ja.lyzhenkov.arrayset;

import java.util.*;
import java.util.stream.Collectors;

public class ArraySet<E> extends AbstractSet<E> implements NavigableSet<E> {

    private final MyArrayList<E> elements;
    private final Comparator<? super E> comp;

    public ArraySet(Collection<? extends E> collection, Comparator<? super E> comp) {
        this.comp = comp;
        this.elements = new MyArrayList<>(collection.stream().collect(Collectors.toCollection(() -> new TreeSet<>(comp))));
    }

    private ArraySet(MyArrayList<E> elements, Comparator<? super E> comp) {
        this.elements = elements;
        this.comp = comp;
    }

    public ArraySet(Collection<? extends E> collection) {
        this(collection, null);
    }

    public ArraySet() {
        this(Collections.emptyList(), null);
    }

    private int compare(final E left, final E right) {
        if (comp != null) {
            return comp.compare(left, right);
        }
        return new TreeSet<>(List.of(left, right)).getLast().equals(right) ? -1 : 1;
    }

    //  [from, to)
    @Override
    public NavigableSet<E> subSet(E fromElement, E toElement) {
        return subSet(fromElement, true, toElement, false);
    }

    //  [0, to)
    @Override
    public SortedSet<E> headSet(E toElement) {
        return headSet(toElement, false);
    }

    //  [from, size)
    @Override
    public SortedSet<E> tailSet(E fromElement) {
        return tailSet(fromElement, true);
    }

    private int getIndex(final E element, final boolean inclusive, final boolean isMin) {
        final int index = Collections.binarySearch(elements, element, comp);
        if (index >= 0) {
            if (inclusive) {
                return index + (isMin ? 0 : 1);
            }
            return index + (isMin ? 1 : 0);
        }
        return -(index + 1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean contains(Object o) {
        return Collections.binarySearch(elements, (E) o, comp) >= 0;
    }

    private boolean checkIndex(int index) {
        return 0 <= index && index < size();
    }

    //    < e
    @Override
    public E lower(E e) {
        return checkAndGetElement(getIndex(e, false, false) - 1);
    }

    //    <= e
    @Override
    public E floor(E e) {
        return checkAndGetElement(getIndex(e, true, false) - 1);
    }

    //    >= e
    @Override
    public E ceiling(E e) {
        return checkAndGetElement(getIndex(e, true, true));
    }

    //    > e
    @Override
    public E higher(E e) {
        return checkAndGetElement(getIndex(e, false, true));
    }

    private E checkAndGetElement(final int index) {
        return checkIndex(index) ? elements.get(index) : null;
    }

    @Override
    public NavigableSet<E> descendingSet() {
        return new ArraySet<>(elements.reverse(), Collections.reverseOrder(comp));
    }

    @Override
    public Iterator<E> descendingIterator() {
        return descendingSet().iterator();
    }

    @Override
    public NavigableSet<E> subSet(E fromElement, boolean fromInclusive, E toElement, boolean toInclusive) {
        if (compare(fromElement, toElement) > 0) {
            throw new IllegalArgumentException("Incorrect cut boundaries");
        }
        final int from = getIndex(fromElement, fromInclusive, true);
        final int to = getIndex(toElement, toInclusive, false);
        if (from > to) {
            return new ArraySet<>(List.of(), comp);
        }
        return new ArraySet<>(elements.subList(from, to), comp);
    }

    @Override
    public NavigableSet<E> headSet(E toElement, boolean inclusive) {
        final int to = getIndex(toElement, inclusive, false);
        return new ArraySet<>(elements.subList(0, to), comp);
    }

    @Override
    public NavigableSet<E> tailSet(E fromElement, boolean inclusive) {
        final int from = getIndex(fromElement, inclusive, true);
        return new ArraySet<>(elements.subList(from, size()), comp);
    }

    @Override
    public E first() {
        checkIsEmpty();
        return elements.getFirst();
    }

    @Override
    public E last() {
        checkIsEmpty();
        return elements.getLast();
    }

    @Override
    public Iterator<E> iterator() {
        return elements.iterator();
    }

    @Override
    public int size() {
        return elements.size();
    }

    @Override
    public boolean isEmpty() {
        return elements.isEmpty();
    }

    @Override
    public Comparator<? super E> comparator() {
        return comp;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException("ArraySet is immutable");
    }

    @Override
    public E pollFirst() {
        throw new UnsupportedOperationException("ArraySet is immutable");
    }

    @Override
    public E pollLast() {
        throw new UnsupportedOperationException("ArraySet is immutable");
    }

    private void checkIsEmpty() {
        if (isEmpty()) {
            throw new NoSuchElementException("Collection is empty");
        }
    }
}
