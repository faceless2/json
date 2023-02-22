package com.bfo.box;

import java.util.*;

@SuppressWarnings("unchecked")
class BoxList<T extends Box> extends AbstractList<T> {
    
    // Efficiency be damned, lists will be short
    private final Box owner;
    private final Class<T> clazz;

    BoxList(Box owner, Class<T> t) {
        this.owner = owner;
        this.clazz = t;
    }

    @Override public int size() {
        int count = 0;
        for (Box b=owner.first();b!=null;b=b.next()) {
            if (is(b)) {
                count++;
            }
        }
        return count;
    }

    @Override public T get(int i) {
        final int ii = i;
        for (Box b=owner.first();b!=null;b=b.next()) {
            if (is(b) && i-- == 0) {
                return (T)b;
            }
        }
        throw new IndexOutOfBoundsException(Integer.toString(ii));
    }

    @Override public T set(int i, T t) {
        if (!is(t)) {
            throw new IllegalArgumentException(t.toString());
        }
        final int ii = i;
        for (Box b=owner.first();b!=null;b=b.next()) {
            if (is(b) && i-- == 0) {
                preadd(t);
                preremove((T)b);
                b.replace(t);
                postremove((T)b);
                postadd(t);
                return (T)b;
            }
        }
        throw new IndexOutOfBoundsException(Integer.toString(ii));
    }

    @Override public T remove(int i) {
        final int ii = i;
        for (Box b=owner.first();b!=null;b=b.next()) {
            if (is(b) && i-- == 0) {
                preremove((T)b);
                b.replace(null);
                postremove((T)b);
                return (T)b;
            }
        }
        throw new IndexOutOfBoundsException(Integer.toString(ii));
    }

    @Override public boolean add(T t) {
        if (!is(t)) {
            throw new IllegalArgumentException(t.toString());
        }
        preadd(t);
        owner.add(t);
        postadd(t);
        return true;
    }

    boolean is(Box b) {
        return clazz.isInstance(b);
    }

    void preadd(T t) {
    }
    void postadd(T t) {
    }
    void preremove(T t) {
    }
    void postremove(T t) {
    }

}
