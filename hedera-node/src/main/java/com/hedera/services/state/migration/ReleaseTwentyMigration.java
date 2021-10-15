package com.hedera.services.state.migration;

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

import com.hedera.services.ServicesState;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.EntityExpiryMapFactory;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETION_TIME;
import static com.hedera.services.state.migration.StateChildIndices.STORAGE;
import static com.hedera.services.utils.MiscUtils.forEach;
import static java.lang.Long.parseLong;

public class ReleaseTwentyMigration {
	/**
	 * Migrates all non-contract storage data in the {@link com.swirlds.blob.BinaryObjectStore} into a
	 * {@code MerkleMap} (which will be a {@code VirtualMap} as soon as SDK supports it).
	 *
	 * It then overwrites the existing {@code MerkleMap<String, MerkleOptionalBlob> storage()} at position
	 * {@link StateChildIndices#STORAGE} with this new map that contains equivalent data, but <b>outside</b>
	 * the {@link com.swirlds.blob.BinaryObjectStore}.
	 *
	 * Specifically (see https://github.com/hashgraph/hedera-services/issues/2319), the method uses
	 * {@link com.hedera.services.utils.MiscUtils#forEach(MerkleMap, BiConsumer)} to iterate over the
	 * {@code (String, MerkleOptionalBlob)} pairs in the storage map. The migration is a follows:
	 * <ul>
	 *     <li>When the key matches the {@link com.hedera.services.files.DataMapFactory#LEGACY_PATH_PATTERN},
	 *     then the corresponding blob's data is migrated to a {@code MerkleMap} entry whose key has
	 *     type {@link com.hedera.services.state.merkle.internals.BlobKey.BlobType#FILE_DATA}, and entity
	 *     number as parsed from the key using the pattern.</li>
	 *     <li>...</li>
	 * </ul>
	 *
	 * @param initializingState
	 */

	private static final Logger log = LogManager.getLogger(ReleaseTwentyMigration.class);

	private static Pattern fileMatcher = DataMapFactory.LEGACY_PATH_PATTERN;
	private static Pattern fileMetaDataMatcher = MetadataMapFactory.LEGACY_PATH_PATTERN;
	private static Pattern byteCodeMatcher = AddressKeyedMapFactory.LEGACY_BYTECODE_PATH_PATTERN;
	private static Pattern systemDeleteMatcher = EntityExpiryMapFactory.LEGACY_PATH_PATTERN;

	private static MerkleMap<BlobKey, MerkleBlob> virtualMap = new MerkleMap<>();

	public ReleaseTwentyMigration() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static void replaceStorageMapWithVirtualMap(final ServicesState initializingState,
			final int deserializedVersion) {
		log.info("Migrating state from version {} to {}", deserializedVersion, StateVersions.RELEASE_TWENTY_VERSION);

		final MerkleMap<String, MerkleOptionalBlob> binaryObjectStorage = initializingState.getChild(
				STORAGE);

		forEach(binaryObjectStorage, (key, value) -> {
			var data = value.getData();
			var blobType = getBlobType(key);
			insertEntityToMerkleMap(key, blobType, data);
		});

		initializingState.setChild(STORAGE, virtualMap);
	}

	private static BlobKey.BlobType getBlobType(String key) {
		if (isFileData(key)) {
			return FILE_DATA;
		} else if (isFileMetaData(key)) {
			return FILE_METADATA;
		} else if (isContractByteCode(key)) {
			return BYTECODE;
		} else if (isSystemDeletedFileData(key)) {
			return SYSTEM_DELETION_TIME;
		}
		return null;
	}

	private static void insertEntityToMerkleMap(final String key, final BlobKey.BlobType blobType, final byte[] data) {
		BlobKey virtualMapKey = new BlobKey(blobType, getEntityNum(key));
		MerkleBlob virtualMapVal = new MerkleBlob(data);
		virtualMap.put(virtualMapKey, virtualMapVal);
	}

	private static boolean isFileData(final String key) {
		return fileMatcher.matcher(key).matches();
	}

	private static boolean isFileMetaData(final String key) {
		return fileMetaDataMatcher.matcher(key).matches();
	}

	private static boolean isContractByteCode(final String key) {
		return byteCodeMatcher.matcher(key).matches();
	}

	private static boolean isSystemDeletedFileData(final String key) {
		return systemDeleteMatcher.matcher(key).matches();
	}

	/**
	 * As the string we are parsing matches /0/f{num} for file data, /0/k{num} for file metadata, /0/s{num} for contract
	 * bytecode, and /0/e{num} for system deleted files, character at fifth position is used to recognize the type of
	 * blob.
	 *
	 * @param key
	 * @return
	 */
	private static long getEntityNum(final String key) {
		return parseLong(String.valueOf(key.charAt(5)));
	}
}
