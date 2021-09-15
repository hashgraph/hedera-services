package com.hedera.services.store.contracts.repository;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ByteArrayMap<V> implements Map<byte[], V> {
    private final Map<ByteArrayWrapper, V> delegate;

    public ByteArrayMap() {
        this(new HashMap());
    }

    public ByteArrayMap(Map<ByteArrayWrapper, V> delegate) {
        this.delegate = delegate;
    }

    public int size() {
        return this.delegate.size();
    }

    public boolean isEmpty() {
        return this.delegate.isEmpty();
    }

    public boolean containsKey(Object key) {
        return this.delegate.containsKey(new ByteArrayWrapper((byte[])((byte[])key)));
    }

    public boolean containsValue(Object value) {
        return this.delegate.containsValue(value);
    }

    public V get(Object key) {
        return this.delegate.get(new ByteArrayWrapper((byte[])((byte[])key)));
    }

    public V put(byte[] key, V value) {
        return this.delegate.put(new ByteArrayWrapper(key), value);
    }

    public V remove(Object key) {
        return this.delegate.remove(new ByteArrayWrapper((byte[])((byte[])key)));
    }

    public void putAll(Map<? extends byte[], ? extends V> m) {
        Iterator var2 = m.entrySet().iterator();

        while(var2.hasNext()) {
            Entry<? extends byte[], ? extends V> entry = (Entry)var2.next();
            this.delegate.put(new ByteArrayWrapper((byte[])entry.getKey()), entry.getValue());
        }

    }

    public void clear() {
        this.delegate.clear();
    }

    public Set<byte[]> keySet() {
        return new ByteArraySet(new SetAdapter(this.delegate));
    }

    public Collection<V> values() {
        return this.delegate.values();
    }

    public Set<Entry<byte[], V>> entrySet() {
        return new ByteArrayMap.MapEntrySet(this.delegate.entrySet());
    }

    public boolean equals(Object o) {
        return this.delegate.equals(o);
    }

    public int hashCode() {
        return this.delegate.hashCode();
    }

    public String toString() {
        return this.delegate.toString();
    }

    private class MapEntrySet implements Set<Entry<byte[], V>> {
        private final Set<Entry<ByteArrayWrapper, V>> delegate;

        private MapEntrySet(Set<Entry<ByteArrayWrapper, V>> delegate) {
            this.delegate = delegate;
        }

        public int size() {
            return this.delegate.size();
        }

        public boolean isEmpty() {
            return this.delegate.isEmpty();
        }

        public boolean contains(Object o) {
            throw new RuntimeException("Not implemented");
        }

        public Iterator<Entry<byte[], V>> iterator() {
            final Iterator<Entry<ByteArrayWrapper, V>> it = this.delegate.iterator();
            return new Iterator<Entry<byte[], V>>() {
                public boolean hasNext() {
                    return it.hasNext();
                }

                public Entry<byte[], V> next() {
                    Entry<ByteArrayWrapper, V> next = (Entry)it.next();
                    return new AbstractMap.SimpleImmutableEntry(((ByteArrayWrapper)next.getKey()).getData(), next.getValue());
                }

                public void remove() {
                    it.remove();
                }
            };
        }

        public Object[] toArray() {
            throw new RuntimeException("Not implemented");
        }

        public <T> T[] toArray(T[] a) {
            throw new RuntimeException("Not implemented");
        }

        public boolean add(Entry<byte[], V> vEntry) {
            throw new RuntimeException("Not implemented");
        }

        public boolean remove(Object o) {
            throw new RuntimeException("Not implemented");
        }

        public boolean containsAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        public boolean addAll(Collection<? extends Entry<byte[], V>> c) {
            throw new RuntimeException("Not implemented");
        }

        public boolean retainAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        public boolean removeAll(Collection<?> c) {
            throw new RuntimeException("Not implemented");
        }

        public void clear() {
            throw new RuntimeException("Not implemented");
        }
    }
}
