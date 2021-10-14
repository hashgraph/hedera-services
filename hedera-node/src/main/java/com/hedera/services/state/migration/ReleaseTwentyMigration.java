package com.hedera.services.state.migration;

import com.hedera.services.ServicesState;
import com.swirlds.merkle.map.MerkleMap;

import java.util.function.BiConsumer;

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
	public static void moveLargeFcmsToBinaryRoutePositions(final ServicesState initializingState) {
		/* TODO */
	}
}
