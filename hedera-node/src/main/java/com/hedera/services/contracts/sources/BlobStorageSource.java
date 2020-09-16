package com.hedera.services.contracts.sources;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import org.ethereum.datasource.DbSettings;
import org.ethereum.datasource.DbSource;

import java.util.Map;
import java.util.Set;

public class BlobStorageSource implements DbSource<byte[]> {
	private String name = "<N/A>";

	private final Map<byte[], byte[]> blobDelegate;

	public BlobStorageSource(Map<byte[], byte[]> blobDelegate) {
		this.blobDelegate = blobDelegate;
	}

	@Override
	public byte[] get(byte[] address) {
		return blobDelegate.get(address);
	}

	@Override
	public void put(byte[] address, byte[] storage) {
		blobDelegate.put(address, storage);
	}

	@Override
	public void delete(byte[] key) {
		blobDelegate.remove(key);
	}

	@Override
	public boolean flush() {
		return false;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void setName(String name) {
		this.name = name;
	}


	@Override
	public boolean isAlive() {
		return false;
	}

	@Override
	public void init() {
		/* No-op. */
	}

	@Override
	public void init(DbSettings settings) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void updateBatch(Map<byte[], byte[]> rows) {
		/* No-op?! */
	}

	@Override
	public void close() {
		/* No-op. */
	}

	@Override
	public Set<byte[]> keys() throws RuntimeException {
		throw new CannotConstructKeysException("Key-set cannot be constructed for blob storage source.");
	}

	@Override
	public void reset() {
		throw new UnsupportedOperationException();
	}

	@Override
	public byte[] prefixLookup(byte[] key, int prefixBytes) {
		throw new UnsupportedOperationException();
	}

	public static class CannotConstructKeysException extends RuntimeException{
		public CannotConstructKeysException(String message){
			super(message);
		}
	}

}
