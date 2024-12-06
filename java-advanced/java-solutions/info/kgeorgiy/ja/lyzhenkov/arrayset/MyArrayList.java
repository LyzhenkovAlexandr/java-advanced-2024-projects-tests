package info.kgeorgiy.ja.lyzhenkov.arrayset;

import java.util.*;

public class MyArrayList<E> extends AbstractList<E> implements RandomAccess {

    private final List<E> elements;
    private final boolean isReversed;

    public MyArrayList(Collection<E> collection) {
        this.elements = new ArrayList<>(collection);
        this.isReversed = false;
    }

    public MyArrayList(List<E> elements, boolean isReversed) {
        this.elements = elements;
        this.isReversed = isReversed;
    }

    public MyArrayList<E> reverse() {
        return new MyArrayList<>(elements, !isReversed);
    }

    @Override
    public MyArrayList<E> subList(int fromIndex, int toIndex) {
        if (!isReversed) {
            return new MyArrayList<>(elements.subList(fromIndex, toIndex), false);
        }
        return new MyArrayList<>(elements.subList(getRealIndex(toIndex - 1), getRealIndex(fromIndex) + 1), isReversed);
    }

    private int getRealIndex(final int index) {
        return isReversed ? size() - index - 1 : index;
    }

    @Override
    public E get(int index) {
        return elements.get(getRealIndex(index));
    }

    @Override
    public int size() {
        return elements.size();
    }
}
