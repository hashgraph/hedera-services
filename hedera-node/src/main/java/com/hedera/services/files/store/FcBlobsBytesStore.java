package com.hedera.services.files.store;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETION_TIME;

public class FcBlobsBytesStore extends AbstractMap<String, byte[]> {
	private static final Logger log = LogManager.getLogger(FcBlobsBytesStore.class);
	private final Supplier<MerkleMap<BlobKey, MerkleBlob>> blobSupplier;

	public FcBlobsBytesStore(Supplier<MerkleMap<BlobKey, MerkleBlob>> pathedBlobs) {
		this.blobSupplier = pathedBlobs;
	}

	/**
	 * As the string we are parsing matches /0/f{num} for file data, /0/k{num} for file metadata, /0/s{num} for contract
	 * bytecode, and /0/e{num} for system deleted files, character at fifth position is used to recognize the type of
	 * blob and entity number
	 *
	 * @param key
	 * @return
	 */
	private BlobKey at(Object key) {
		final String path = (String) key;
		final BlobKey.BlobType type = getType(path.charAt(3));
		final long entityNum = Long.parseLong(String.valueOf(path.charAt(5)));
		return new BlobKey(type, entityNum);
	}

	@Override
	public void clear() {
		blobSupplier.get().clear();
	}

	/**
	 * Removes the blob at the given path.
	 *
	 * <B>NOTE:</B> This method breaks the standard {@code Map} contract,
	 * and does not return the contents of the removed blob.
	 *
	 * @param path
	 * 		the path of the blob
	 * @return {@code null}
	 */
	@Override
	public byte[] remove(Object path) {
		blobSupplier.get().remove(at(path));
		return null;
	}

	/**
	 * Replaces the blob at the given path with the given contents.
	 *
	 * <B>NOTE:</B> This method breaks the standard {@code Map} contract,
	 * and does not return the contents of the previous blob.
	 *
	 * @param path
	 * 		the path of the blob
	 * @param value
	 * 		the contents to be set
	 * @return {@code null}
	 */
	@Override
	public byte[] put(String path, byte[] value) {
		var meta = at(path);
		if (blobSupplier.get().containsKey(meta)) {
			final var blob = blobSupplier.get().getForModify(meta);
			blob.setData(value);
			if (log.isDebugEnabled()) {
				log.debug("Modifying to {} new bytes (hash = {}) @ '{}'", value.length, blob.getHash(), path);
			}
		} else {
			final MerkleBlob blob = new MerkleBlob(value);
			if (log.isDebugEnabled()) {
				log.debug("Putting {} new bytes (hash = {}) @ '{}'", value.length, blob.getHash(), path);
			}
			blobSupplier.get().put(at(path), blob);
		}
		return null;
	}

	@Override
	public byte[] get(Object path) {
		return Optional.ofNullable(blobSupplier.get().get(at(path)))
				.map(MerkleBlob::getData)
				.orElse(null);
	}

	@Override
	public boolean containsKey(Object path) {
		return blobSupplier.get().containsKey(at(path));
	}

	@Override
	public boolean isEmpty() {
		return blobSupplier.get().isEmpty();
	}

	@Override
	public int size() {
		return blobSupplier.get().size();
	}

	@Override
	public Set<Entry<String, byte[]>> entrySet() {
		throw new UnsupportedOperationException();
	}

	public static BlobKey.BlobType getType(char index) {
		switch (index) {
			case 'f':
				return FILE_DATA;
			case 'k':
				return FILE_METADATA;
			case 's':
				return BYTECODE;
			case 'e':
				return SYSTEM_DELETION_TIME;
			default:
				throw new IllegalArgumentException("Unidentified type of blob");
		}
	}

}
