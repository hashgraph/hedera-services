/*
 * Copyright (c) [2016] [ <ether.camp> ]
 * This file is part of the ethereumJ library.
 *
 * The ethereumJ library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ethereumJ library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ethereumJ library. If not, see <http://www.gnu.org/licenses/>.
 */
package org.ethereum.db;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.datasource.AbstractCachedSource;
import org.ethereum.datasource.CachedSource;
import org.ethereum.datasource.MultiCache;
import org.ethereum.datasource.ReadWriteCache;
import org.ethereum.datasource.Serializers;
import org.ethereum.datasource.SimplifiedWriteCache;
import org.ethereum.datasource.Source;
import org.ethereum.datasource.SourceCodec;
import org.ethereum.datasource.StoragePersistence;
import org.ethereum.datasource.WriteCache;
import org.ethereum.datasource.WriteCache.CacheEntry;
import org.ethereum.datasource.WriteCache.CacheType;
import org.ethereum.trie.SecureTrie;
import org.ethereum.trie.Trie;
import org.ethereum.trie.TrieImpl;
import org.ethereum.vm.DataWord;

/**
 * Created by Anton Nashatyrev on 07.10.2016.
 */
public class ServicesRepositoryRoot extends ServicesRepositoryImpl {
	private static final Logger log = LogManager.getLogger("db");
	private static int DWORD_BYTES = 32;

	private Trie<byte[]> stateTrie;
	private StoragePersistence storagePersistence;
	private Source<byte[], byte[]> bytecodeDb;
	private CachedSource.BytesKey<byte[]> trieCache;

	public ServicesRepositoryRoot(
			final Source<byte[], AccountState> fcMapSrc,
			final Source<byte[], byte[]> bytecodeDb
	) {
		this.bytecodeDb = bytecodeDb;
		trieCache = new WriteCache.BytesKey<>(bytecodeDb, WriteCache.CacheType.COUNTING);
		stateTrie = new SecureTrie(trieCache, null);

		final SimplifiedWriteCache.BytesKey<AccountState> accountStateCache = new SimplifiedWriteCache.BytesKey<>(
				fcMapSrc);

		MultiCache<StorageCache> storageCache = new MultiStorageCache();
		Source<byte[], byte[]> codeCache = new SimplifiedWriteCache.BytesKey<>(bytecodeDb);
		init(accountStateCache, codeCache, storageCache);
	}

	@Override
	public synchronized void commit() {
		super.commit();
		stateTrie.flush();
		trieCache.flush();
	}

	@Override
	public synchronized void flush() {
		commit();
	}

	@Override
	public synchronized byte[] getRoot() {
		storageCache.flush();
		accountStateCache.flush();
		return stateTrie.getRootHash();
	}

	@Override
	public Repository getSnapshotTo(byte[] root) {
		throw new IllegalStateException("Not Suppported");
	}

	@Override
	public synchronized String dumpStateTrie() {
		return ((TrieImpl) stateTrie).dumpTrie();
	}

	@Override
	public synchronized void syncToRoot(byte[] root) {
		stateTrie.setRoot(root);
	}

	protected TrieImpl createTrie(CachedSource.BytesKey<byte[]> trieCache, byte[] root) {
		return new SecureTrie(trieCache, root);
	}

	public void commitAndSaveRoot() {
		commit();
	}

	public void setStoragePersistence(StoragePersistence storagePersistence) {
		this.storagePersistence = storagePersistence;
	}

	public boolean persistStorageCache(int maxStorageKb) {
		long start = System.currentTimeMillis();
		MultiCache<? extends CachedSource<DataWord, DataWord>> currStorageCache = getStorageCache();
		Map<byte[], byte[]> cachesToPersist = currStorageCache.getSerializedCache();
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
				AccountState currAccount = getHGCAccount(address);
				long expirationTime = currAccount.getExpirationTime();
				long createTime = currAccount.getCreateTimeMs();
				long before = System.currentTimeMillis();
				this.storagePersistence.persist(address, cachesToPersist.get(address), expirationTime, createTime);
				long after = System.currentTimeMillis();
				log.info(String.format(
						"%d ms to persist %d bytes from storage cache",
						(after - before),
						cachesToPersist.get(address).length));
			}
		}
		emptyStorageCache();
		long end = System.currentTimeMillis();
		log.info(String.format("%d ms to persist all caches", (end - start)));

		return true;
	}

	public void emptyStorageCache() {
		storageCache = new MultiStorageCache();
	}

	private static class StorageCache extends ReadWriteCache<DataWord, DataWord> {
		Trie<byte[]> trie;

		StorageCache(Trie<byte[]> trie) {
			super(
					new SourceCodec<>(trie, Serializers.StorageKeySerializer, Serializers.StorageValueSerializer),
					WriteCache.CacheType.SIMPLE);
			this.trie = trie;
		}

		StorageCache(Trie<byte[]> trie, WriteCache<DataWord, DataWord> writeCache) {
			super(new SourceCodec<>(trie, Serializers.StorageKeySerializer,
					Serializers.StorageValueSerializer), writeCache);
			this.trie = trie;
		}

		WriteCache<DataWord, DataWord> getWriteCache() {
			return this.writeCache;
		}
	}

	private class MultiStorageCache extends MultiCache<StorageCache> {
		MultiStorageCache() {
			super(null);
		}

		@Override
		protected synchronized StorageCache create(byte[] key, StorageCache srcCache) {
			AccountState accountState = accountStateCache.get(key);
			TrieImpl storageTrie = createTrie(trieCache, accountState == null ? null : accountState.getStateRoot());
			return new StorageCache(storageTrie);
		}

		@Override
		protected synchronized boolean flushChild(byte[] key, StorageCache childCache) {
			if (super.flushChild(key, childCache)) {
				if (childCache != null) {
					AccountState storageOwnerAcct = accountStateCache.get(key);
					childCache.trie.flush();
					byte[] rootHash = childCache.trie.getRootHash();
					accountStateCache.put(key, storageOwnerAcct.withStateRoot(rootHash));
					return true;
				} else {
					return true;
				}
			} else {
				return false;
			}
		}

		@Override
		public synchronized StorageCache get(byte[] key) {
			if (storagePersistence != null) {
				AbstractCachedSource.Entry<StorageCache> ownCacheEntry = getCached(key);
				StorageCache ownCache = ownCacheEntry == null ? null : ownCacheEntry.value();

				if (ownCache == null) {
					if (storagePersistence.storageExist(key)) {
						long start = System.currentTimeMillis();
						byte[] previouslyPersisted = storagePersistence.get(key);
						WriteCache<DataWord, DataWord> cacheToPut = deserializeCacheMap(previouslyPersisted);
						AccountState accountState = accountStateCache.get(key);
						TrieImpl storageTrie =
								createTrie(trieCache, accountState == null ? null : accountState.getStateRoot());
						StorageCache storageCacheToPut = new StorageCache(storageTrie, cacheToPut);
						super.put(key, storageCacheToPut);
						long end = System.currentTimeMillis();
						log.info(String.format("Time to read one contract cache from storage: %d ms", (end - start)));
						return storageCacheToPut;
					} else {
						return super.get(key);
					}
				} else {
					return super.get(key);
				}
			} else {
				return super.get(key);
			}
		}

		@Override
		public void put(byte[] key, StorageCache val) {
			super.put(key, val);
		}

		@Override
		public synchronized Map<byte[], byte[]> getSerializedCache() {
			Map<byte[], byte[]> serializedCache = new HashMap<>();
			for (byte[] currAddress : this.writeCache.getCache().keySet()) {
				if (this.get(currAddress).getWriteCache().hasModified()) {
					Map<DataWord, CacheEntry<DataWord>> underlyingCache = this.get(
							currAddress).getWriteCache().getCache();
					byte[] serializedCacheMap = serializeCacheMap(underlyingCache);
					serializedCache.put(currAddress, serializedCacheMap);
				}
			}
			return serializedCache;
		}

		private byte[] serializeCacheMap(Map<DataWord, CacheEntry<DataWord>> cacheMap) {
			ArrayList<DataWord> keys = new ArrayList<>(cacheMap.keySet());
			Collections.sort(keys);
			int offset = 0;
			int skips = 0;
			byte[] result = new byte[keys.size() * DWORD_BYTES * 2];
			for (DataWord key : keys) {
				CacheEntry<DataWord> currEntry = cacheMap.get(key);
				if (currEntry != null && currEntry.value() != null && currEntry.value().getData() != null) {
					System.arraycopy(key.getData(), 0, result, offset, DWORD_BYTES);
					offset += DWORD_BYTES;
					System.arraycopy(cacheMap.get(key).value().getData(), 0, result, offset, DWORD_BYTES);
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

		private WriteCache<DataWord, DataWord> deserializeCacheMap(byte[] serializedMap) {
			WriteCache<DataWord, DataWord> cacheToPut = new WriteCache<DataWord, DataWord>(null, CacheType.SIMPLE);

			int offset = 0;
			while (offset < serializedMap.length) {
				byte[] keyBytes = new byte[DWORD_BYTES];
				byte[] valBytes = new byte[DWORD_BYTES];
				System.arraycopy(serializedMap, offset, keyBytes, 0, DWORD_BYTES);
				offset += DWORD_BYTES;
				System.arraycopy(serializedMap, offset, valBytes, 0, DWORD_BYTES);
				offset += DWORD_BYTES;
				cacheToPut.put(new DataWord(keyBytes), new DataWord(valBytes));
			}
			cacheToPut.resetModified();
			return cacheToPut;
		}

		@Override
		public synchronized Collection<byte[]> getCacheKeyset() {
			if (this.writeCache != null && this.writeCache.getCache() != null) {
				return this.writeCache.getCache().keySet();
			} else {
				return new HashSet<>();
			}
		}
	}
}
