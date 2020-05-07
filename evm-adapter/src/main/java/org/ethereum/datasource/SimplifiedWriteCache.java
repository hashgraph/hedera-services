package org.ethereum.datasource;

import com.googlecode.concurentlocks.ReadWriteUpdateLock;
import com.googlecode.concurentlocks.ReentrantReadWriteUpdateLock;
import org.ethereum.util.ALock;
import org.ethereum.util.ByteArrayMap;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class SimplifiedWriteCache<Key, Value> extends AbstractCachedSource<Key, Value> {
  

    public static abstract class CacheEntry<V> implements Entry<V>,Serializable{
        // dedicated value instance which indicates that the entry was deleted
        // (ref counter decremented) but we don't know actual value behind it
        static final Object UNKNOWN_VALUE = new Object();

        V value;
        int counter = 0;

        protected CacheEntry(V value) {
            this.value = value;
        }

        protected abstract void deleted();

        protected abstract void added();

        protected abstract V getValue();

        @Override
        public V value() {
            V v = getValue();
            return v == UNKNOWN_VALUE ? null : v;
        }
    }

    private static final class SimpleCacheEntry<V> extends CacheEntry<V> {
        public SimpleCacheEntry(V value) {
            super(value);
        }

        public void deleted() {
            counter = -1;
        }

        public void added() {
            counter = 1;
        }

        @Override
        public V getValue() {
            return counter < 0 ? null : value;
        }
    }

    protected volatile Map<Key, CacheEntry<Value>> cache = new HashMap<>();

    protected ReadWriteUpdateLock rwuLock = new ReentrantReadWriteUpdateLock();
    protected ALock readLock = new ALock(rwuLock.readLock());
    protected ALock writeLock = new ALock(rwuLock.writeLock());
    protected ALock updateLock = new ALock(rwuLock.updateLock());

    private boolean checked = false;

    public SimplifiedWriteCache(Source<Key, Value> src) {
        super(src);
    }

    public SimplifiedWriteCache<Key, Value> withCache(Map<Key, CacheEntry<Value>> cache) {
        this.cache = cache;
        return this;
    }

    @Override
    public Collection<Key> getModified() {
        try (ALock l = readLock.lock()){
            return cache.keySet();
        }
    }

    @Override
    public boolean hasModified() {
        return !cache.isEmpty();
    }

    private CacheEntry<Value> createCacheEntry(Value val) {        
            return new SimpleCacheEntry<>(val);
    }

    @Override
    public void put(Key key, Value val) {
        if (val == null)  {
            delete(key);
            return;
        }
        try (ALock l = writeLock.lock()){
            CacheEntry<Value> curVal = cache.get(key);
            if (curVal == null) {
                curVal = createCacheEntry(val);
                CacheEntry<Value> oldVal = cache.put(key, curVal);
                if (oldVal != null) {
                    cacheRemoved(key, oldVal.value == unknownValue() ? null : oldVal.value);
                }
                cacheAdded(key, curVal.value);
            }
            // assigning for non-counting cache only
            // for counting cache the value should be immutable (see HashedKeySource)
            curVal.value = val;
            curVal.added();
        }
    }

    @Override
    public Value get(Key key) {
        try (ALock l = readLock.lock()){
            CacheEntry<Value> curVal = cache.get(key);
            if (curVal == null) {
                return getSource() == null ? null : getSource().get(key);
            } else {
                Value value = curVal.getValue();
                if (value == unknownValue()) {
                    return getSource() == null ? null : getSource().get(key);
                } else {
                    return value;
                }
            }
        }
    }

    @Override
    public void delete(Key key) {
        try (ALock l = writeLock.lock()){
            CacheEntry<Value> curVal = cache.get(key);
            if (curVal == null) {
                curVal = createCacheEntry(getSource() == null ? null : unknownValue());
                CacheEntry<Value> oldVal = cache.put(key, curVal);
                if (oldVal != null) {
                    cacheRemoved(key, oldVal.value);
                }
                cacheAdded(key, curVal.value == unknownValue() ? null : curVal.value);
            }
            curVal.deleted();
        }
    }

    @Override
    public boolean flush() {
        boolean ret = false;
        try (ALock l = updateLock.lock()){
            for (Map.Entry<Key, CacheEntry<Value>> entry : cache.entrySet()) {
                if (entry.getValue().counter > 0) {
                    for (int i = 0; i < entry.getValue().counter; i++) {
                        getSource().put(entry.getKey(), entry.getValue().value);
                    }
                    ret = true;
                } else if (entry.getValue().counter < 0) {
                    for (int i = 0; i > entry.getValue().counter; i--) {
                        getSource().delete(entry.getKey());
                    }
                    ret = true;
                }
            }
            if (flushSource) {
                getSource().flush();
            }
            try (ALock l1 = writeLock.lock()){
                cache.clear();
                cacheCleared();
            }
            return ret;
        }
    }

    @Override
    protected boolean flushImpl() {
        return false;
    }

    private Value unknownValue() {
        return (Value) CacheEntry.UNKNOWN_VALUE;
    }

    public Entry<Value> getCached(Key key) {
        try (ALock l = readLock.lock()){
            CacheEntry<Value> entry = cache.get(key);
            if (entry == null || entry.value == unknownValue()) {
                return null;
            }else {
                return entry;
            }
        }
    }


    public long debugCacheSize() {
        long ret = 0;
        for (Map.Entry<Key, CacheEntry<Value>> entry : cache.entrySet()) {
            ret += keySizeEstimator.estimateSize(entry.getKey());
            ret += valueSizeEstimator.estimateSize(entry.getValue().value());
        }
        return ret;
    }

    /**
     * Shortcut for WriteCache with byte[] keys. Also prevents accidental
     * usage of regular Map implementation (non byte[])
     */
    public static class BytesKey<V> extends SimplifiedWriteCache<byte[], V> implements CachedSource.BytesKey<V> {

        public BytesKey(Source<byte[], V> src) {
            super(src);
            withCache(new ByteArrayMap<CacheEntry<V>>());
        }
    }
    
    public Map<Key, CacheEntry<Value>> getCache(){
    	return this.cache;
    }
    
    public Value getFromSource(Key key) {
      try (ALock l = readLock.lock()){        
        return getSource() == null ? null : getSource().get(key);
      }
  }
}
