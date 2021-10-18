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
import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.MerkleContractStorageValue;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.hedera.services.state.merkle.internals.ContractStorageKey;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.hedera.services.files.store.FcBlobsBytesStore.LEGACY_BLOB_CODE_INDEX;
import static com.hedera.services.files.store.FcBlobsBytesStore.getEntityNumFromPath;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.CONTRACT_STORAGE;
import static com.hedera.services.state.merkle.internals.BlobKey.typeFromCharCode;
import static com.hedera.services.state.merkle.internals.ContractStorageKey.BYTES_PER_UINT256;
import static com.hedera.services.state.migration.StateVersions.RELEASE_TWENTY_VERSION;
import static com.hedera.services.utils.MiscUtils.forEach;

public class ReleaseTwentyMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyMigration.class);

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
	 * {@code (String, MerkleOptionalBlob)} pairs in the storage map; and translate them to equivalent
	 * entries in the new map.
	 *
	 * @param initializingState
	 * 		the saved state being migrated during initialization
	 * @param deserializedVersion
	 * 		for completeness, the version of the saved state
	 */
	public static void migrateFromBinaryObjectStore(
			final ServicesState initializingState,
			final int deserializedVersion
	) {
		log.info("Migrating state from version {} to {}", deserializedVersion, RELEASE_TWENTY_VERSION);

		final MerkleMap<String, MerkleOptionalBlob> legacyBlobs = initializingState.getChild(StateChildIndices.STORAGE);

		final MerkleMap<BlobKey, MerkleBlob> vmBlobsStandIn = new MerkleMap<>();
		final MerkleMap<ContractStorageKey, MerkleContractStorageValue> vmStorageStandIn = new MerkleMap<>();

		final Map<BlobKey.BlobType, AtomicInteger> counts = new EnumMap<>(BlobKey.BlobType.class);
		forEach(legacyBlobs, (path, blob) -> {
			final var blobType = typeFromCharCode(path.charAt(LEGACY_BLOB_CODE_INDEX));
			final var blobEntityNum = getEntityNumFromPath(path);
			if (blobType == CONTRACT_STORAGE) {
				insertPairsFrom(blobEntityNum, blob.getData(), vmStorageStandIn);
			} else {
				final var vKey = new BlobKey(blobType, blobEntityNum);
				final var vBlob = new MerkleBlob(blob.getData());
				vmBlobsStandIn.put(vKey, vBlob);
			}
			counts.computeIfAbsent(blobType, ignore -> new AtomicInteger()).getAndIncrement();
		});

		initializingState.setChild(StateChildIndices.STORAGE, vmBlobsStandIn);
		initializingState.setChild(StateChildIndices.CONTRACT_STORAGE, vmStorageStandIn);

		log.info("Migration complete for:"
						+ "\n  ↪ {} file metadata blobs"
						+ "\n  ↪ {} file data blobs"
						+ "\n  ↪ {} contract bytecode blobs"
						+ "\n  ↪ {} contract storage blobs"
						+ "\n  ↪ {} system-deleted entity expiry blobs",
				counts.get(BlobKey.BlobType.FILE_METADATA).get(),
				counts.get(BlobKey.BlobType.FILE_DATA).get(),
				counts.get(BlobKey.BlobType.CONTRACT_BYTECODE).get(),
				counts.get(CONTRACT_STORAGE).get(),
				counts.get(BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY).get());
	}

	static void insertPairsFrom(
			final long contractNum,
			final byte[] orderedKeyValueStorage,
			final MerkleMap<ContractStorageKey, MerkleContractStorageValue> vmStorageStandIn
	) {
		int offset = 0;

		while (offset < orderedKeyValueStorage.length) {
			final var rawKey = new byte[BYTES_PER_UINT256];
			final var rawValue = new byte[BYTES_PER_UINT256];

			System.arraycopy(orderedKeyValueStorage, offset, rawKey, 0, BYTES_PER_UINT256);
			offset += BYTES_PER_UINT256;
			System.arraycopy(orderedKeyValueStorage, offset, rawValue, 0, BYTES_PER_UINT256);
			offset += BYTES_PER_UINT256;

			final var storageKey = new ContractStorageKey(contractNum, rawKey);
			final var storageValue = new MerkleContractStorageValue();
			storageValue.setValue(rawValue);
			vmStorageStandIn.put(storageKey, storageValue);
		}
	}

	private ReleaseTwentyMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
