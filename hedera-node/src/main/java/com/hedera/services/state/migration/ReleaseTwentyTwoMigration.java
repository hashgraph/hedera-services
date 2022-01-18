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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.utils.EntityNum;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static com.hedera.services.files.store.FcBlobsBytesStore.LEGACY_BLOB_CODE_INDEX;
import static com.hedera.services.state.migration.StateVersions.RELEASE_0220_VERSION;
import static com.hedera.services.utils.MiscUtils.forEach;
import static java.lang.Long.parseLong;
import static org.apache.tuweni.units.bigints.UInt256.SIZE;

public class ReleaseTwentyTwoMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyTwoMigration.class);

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
		log.info("Migrating state from version {} to {}", deserializedVersion, RELEASE_0220_VERSION);

		final var virtualMapFactory = new VirtualMapFactory(JasperDbBuilder::new);
		final MerkleMap<String, MerkleOptionalBlob> legacyBlobs = initializingState.getChild(StateChildIndices.STORAGE);
		final MerkleMap<EntityNum, MerkleAccount> accounts = initializingState.getChild(StateChildIndices.ACCOUNTS);

		final VirtualMap<VirtualBlobKey, VirtualBlobValue> vmBlobs = virtualMapFactory.newVirtualizedBlobs();
		final VirtualMap<ContractKey, ContractValue> vmStorage = virtualMapFactory.newVirtualizedStorage();

		final Map<Long, Integer> kvCounts = new TreeMap<>();
		final Map<Character, AtomicInteger> counts = new HashMap<>();
		forEach(legacyBlobs, (path, blob) -> {
			final var pathCode = path.charAt(LEGACY_BLOB_CODE_INDEX);
			if (pathCode == 'd') {
				final var contractNum = parseLong(path.substring(LEGACY_BLOB_CODE_INDEX + 1));
				insertPairsFrom(contractNum, blob.getData(), kvCounts, vmStorage);
			} else {
				final var vKey = VirtualBlobKey.fromPath(path);
				final var vBlob = new VirtualBlobValue(blob.getData());
				vmBlobs.put(vKey, vBlob);
			}
			counts.computeIfAbsent(pathCode, ignore -> new AtomicInteger()).getAndIncrement();
		});
		initKvCounts(kvCounts, accounts);

		initializingState.setChild(StateChildIndices.STORAGE, vmBlobs);
		initializingState.setChild(StateChildIndices.CONTRACT_STORAGE, vmStorage);

		final var defaultZero = new AtomicInteger();
		final var summaryTpl = """
				Migration complete for:
					\u21AA {} file metadata blobs
					\u21AA {} file data blobs
					\u21AA {} contract bytecode blobs
					\u21AA {} contract storage blobs
					\u21AA {} system-deleted entity expiry blobs""";
		log.info(summaryTpl,
				counts.getOrDefault('k', defaultZero).get(),
				counts.getOrDefault('f', defaultZero).get(),
				counts.getOrDefault('s', defaultZero).get(),
				counts.getOrDefault('d', defaultZero).get(),
				counts.getOrDefault('e', defaultZero).get());
	}

	static void insertPairsFrom(
			final long contractNum,
			final byte[] orderedKeyValueStorage,
			final Map<Long, Integer> kvCounts,
			final VirtualMap<ContractKey, ContractValue> vmStorage
	) {
		int offset = 0;
		int n = 0;
		while (offset < orderedKeyValueStorage.length) {
			final var rawKey = new byte[SIZE];
			final var rawValue = new byte[SIZE];

			System.arraycopy(orderedKeyValueStorage, offset, rawKey, 0, SIZE);
			offset += SIZE;
			System.arraycopy(orderedKeyValueStorage, offset, rawValue, 0, SIZE);
			offset += SIZE;

			if (!Bytes32.wrap(rawValue).isZero()) {
				final var key = new ContractKey(contractNum, rawKey);
				final var value = new ContractValue(rawValue);
				vmStorage.put(key, value);
				n++;
			}
		}
		log.info("Existing contract 0.0.{} had {} non-ZERO key/value pairs", contractNum, n);
		kvCounts.put(contractNum, n);
	}

	static void initKvCounts(final Map<Long, Integer> kvCounts, final MerkleMap<EntityNum, MerkleAccount> accounts ) {
		kvCounts.forEach((contractId, n) -> {
			final var key = EntityNum.fromLong(contractId);
			if (accounts.containsKey(key)) {
				final var mutableAccount = accounts.getForModify(key);
				mutableAccount.setNumContractKvPairs(n);
			}
		});
	}

	private ReleaseTwentyTwoMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
