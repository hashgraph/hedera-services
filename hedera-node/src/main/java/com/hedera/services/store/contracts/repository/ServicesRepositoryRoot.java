package com.hedera.services.store.contracts.repository;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ServicesRepositoryRoot extends ServicesRepositoryImpl {
    private static final int DWORD_BYTES = 32;

    private StoragePersistence storagePersistence;

    public ServicesRepositoryRoot(
            final Source<byte[], AccountState> accountStateSource,
            final Source<byte[], byte[]> codeSource
    ) {
        Source<byte[], byte[]> codeCache = new SimplifiedWriteCache.BytesKey<>(codeSource);
        Source<byte[], AccountState> accountStateCache = new SimplifiedWriteCache.BytesKey<>(accountStateSource);
        MultiCache<ServicesRepositoryRoot.StorageCache> storageCache = new ServicesRepositoryRoot.MultiStorageCache();
        init(accountStateCache, codeCache, storageCache);
    }

    @Override
    public synchronized void flush() {
        commit();
    }

    public void emptyStorageCache() {
        storageCache = new ServicesRepositoryRoot.MultiStorageCache();
    }

    public void setStoragePersistence(StoragePersistence storagePersistence) {
        this.storagePersistence = storagePersistence;
    }

    public boolean flushStorageCacheIfTotalSizeLessThan(int maxStorageKb) {
        long start = System.currentTimeMillis();
        Map<byte[], byte[]> cachesToPersist = storageCache.getSerializedCache();
        if (cachesToPersist != null) {
            ArrayList<byte[]> addresses = new ArrayList<>(cachesToPersist.keySet());
            addresses.sort((left, right) -> {
                for (int i = 0, j = 0; i < left.length && j < right.length; i++, j++) {
                    int a = (left[i] & 0xff);
                    int b = (right[j] & 0xff);
                    if (a != b) {
                        return a - b;
                    }
                }
                return left.length - right.length;
            });
            int totalSize = 0;
            for (byte[] currAddress : addresses) {
                totalSize += cachesToPersist.get(currAddress).length;
                if (totalSize > (1024 * maxStorageKb)) {
                    return false;
                }
            }
            for (byte[] address : addresses) {
                AccountState currAccount = getAccount(address);
                long expirationTime = currAccount.getExpirationTime();
                long createTime = currAccount.getCreateTimeMs();
                long before = System.currentTimeMillis();
                this.storagePersistence.persist(address, cachesToPersist.get(address), expirationTime, createTime);
                long after = System.currentTimeMillis();
            }
        }
        emptyStorageCache();
        long end = System.currentTimeMillis();

        return true;
    }

    private static class StorageCache extends ReadWriteCache<UInt256, UInt256> {
        StorageCache() {
            super(null, WriteCache.CacheType.SIMPLE);
        }

        StorageCache(WriteCache<UInt256, UInt256> writeCache) {
            super(null, writeCache);
        }

        WriteCache<UInt256, UInt256> getWriteCache() {
            return writeCache;
        }
    }

    private class MultiStorageCache extends MultiCache<ServicesRepositoryRoot.StorageCache> {
        MultiStorageCache() {
            super(null);
        }

        @Override
        public synchronized boolean flushImpl() {
            return false;
        }

        @Override
        protected synchronized ServicesRepositoryRoot.StorageCache create(byte[] key, ServicesRepositoryRoot.StorageCache srcCache) {
            return new ServicesRepositoryRoot.StorageCache();
        }

        @Override
        public synchronized ServicesRepositoryRoot.StorageCache get(byte[] key) {
            AbstractCachedSource.Entry<ServicesRepositoryRoot.StorageCache> ownCacheEntry = getCached(key);
            ServicesRepositoryRoot.StorageCache ownCache = ownCacheEntry == null ? null : ownCacheEntry.value();

            if (ownCache == null) {
                if (storagePersistence.storageExist(key)) {
                    long start = System.currentTimeMillis();
                    byte[] previouslyPersisted = storagePersistence.get(key);
                    WriteCache<UInt256, UInt256> cacheToPut = deserializeCacheMap(previouslyPersisted);
                    ownCache = new ServicesRepositoryRoot.StorageCache(cacheToPut);
                    long end = System.currentTimeMillis();
                } else {
                    ownCache = create(key, null);
                }
                put(key, ownCache);
            }

            return ownCache;
        }

        @Override
        public synchronized Map<byte[], byte[]> getSerializedCache() {
            Map<byte[], byte[]> serializedCache = new HashMap<>();
            for (byte[] currAddress : writeCache.getCache().keySet()) {
                ServicesRepositoryRoot.StorageCache storageCache = get(currAddress);
                if (storageCache.getWriteCache().hasModified()) {
                    Map<UInt256, WriteCache.CacheEntry<UInt256>> underlyingCache = storageCache.getWriteCache().getCache();
                    byte[] serializedCacheMap = serializeCacheMap(underlyingCache);
                    serializedCache.put(currAddress, serializedCacheMap);
                }
            }
            return serializedCache;
        }

        private byte[] serializeCacheMap(Map<UInt256, WriteCache.CacheEntry<UInt256>> cacheMap) {
            ArrayList<UInt256> keys = new ArrayList<>(cacheMap.keySet());
            Collections.sort(keys);
            int offset = 0;
            int skips = 0;
            byte[] result = new byte[keys.size() * DWORD_BYTES * 2];
            for (UInt256 key : keys) {
                WriteCache.CacheEntry<UInt256> currEntry = cacheMap.get(key);
                if (currEntry != null && currEntry.value() != null && currEntry.value().toBytes() != null) {
                    System.arraycopy(key.toBytes(), 0, result, offset, DWORD_BYTES);
                    offset += DWORD_BYTES;
                    System.arraycopy(cacheMap.get(key).value().toBytes(), 0, result, offset, DWORD_BYTES);
                    offset += DWORD_BYTES;
                } else {
                    skips += 1;
                }
            }

            if (skips > 0) {
                int newSize = (keys.size() - skips) * DWORD_BYTES * 2;
                byte[] newResult = new byte[newSize];
                System.arraycopy(result, 0, newResult, 0, newSize);
                return newResult;
            }

            return result;
        }

        private WriteCache<UInt256, UInt256> deserializeCacheMap(byte[] serializedMap) {
            WriteCache<UInt256, UInt256> cacheToPut = new WriteCache<UInt256, UInt256>(null, WriteCache.CacheType.SIMPLE);

            int offset = 0;
            while (offset < serializedMap.length) {
                byte[] keyBytes = new byte[DWORD_BYTES];
                byte[] valBytes = new byte[DWORD_BYTES];
                System.arraycopy(serializedMap, offset, keyBytes, 0, DWORD_BYTES);
                offset += DWORD_BYTES;
                System.arraycopy(serializedMap, offset, valBytes, 0, DWORD_BYTES);
                offset += DWORD_BYTES;
                cacheToPut.put( UInt256.fromBytes(Bytes.wrap(keyBytes)), UInt256.fromBytes(Bytes.wrap(valBytes)));
            }
            cacheToPut.resetModified();
            return cacheToPut;
        }
    }
}
