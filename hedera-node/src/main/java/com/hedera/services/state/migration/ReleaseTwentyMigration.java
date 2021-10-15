package com.hedera.services.state.migration;

import com.hedera.services.ServicesState;
import com.hedera.services.contracts.sources.AddressKeyedMapFactory;
import com.hedera.services.files.DataMapFactory;
import com.hedera.services.files.EntityExpiryMapFactory;
import com.hedera.services.files.MetadataMapFactory;
import com.hedera.services.state.merkle.MerkleBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;

import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.BYTECODE;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_DATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.FILE_METADATA;
import static com.hedera.services.state.merkle.internals.BlobKey.BlobType.SYSTEM_DELETION_TIME;
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
	private static MerkleMap<BlobKey, MerkleBlob> virtualMap = new MerkleMap<>();
	private static Pattern fileMatcher = DataMapFactory.LEGACY_PATH_PATTERN;
	private static Pattern fileMetaDataMatcher = MetadataMapFactory.LEGACY_PATH_PATTERN;
	private static Pattern byteCodeMatcher = AddressKeyedMapFactory.LEGACY_BYTECODE_PATH_PATTERN;
	private static Pattern systemDeleteMatcher = EntityExpiryMapFactory.LEGACY_PATH_PATTERN;

	public ReleaseTwentyMigration() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static void moveNonContractStorageToMerkleMap(final ServicesState initializingState) {
		log.info("Migrating to 0.20.0 state ");

		final var binaryObjectStorage = initializingState.storage();

		forEach(binaryObjectStorage, (key, value) -> {
			var data = value.getData();
			if (isFileData(key)) {
				BlobKey.BlobType blobType = FILE_DATA;
				insertEntityToMerkleMap(key, blobType, data);
			} else if (isFileMetaData(key)) {
				BlobKey.BlobType blobType = FILE_METADATA;
				insertEntityToMerkleMap(key, blobType, data);
			} else if (isContractByteCode(key)) {
				BlobKey.BlobType blobType = BYTECODE;
				insertEntityToMerkleMap(key, blobType, data);
			} else if (isSystemDeletedFileData(key)) {
				BlobKey.BlobType blobType = SYSTEM_DELETION_TIME;
				insertEntityToMerkleMap(key, blobType, data);
			}
		});
	}

	private static void insertEntityToMerkleMap(final String key, final BlobKey.BlobType blobType, final byte[] data) {
		BlobKey virtualMapKey = new BlobKey(blobType, parseLong(getEntityNum(key, blobType)));
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

	private static String getEntityNum(final String key, final BlobKey.BlobType type) {
		switch (type) {
			case FILE_DATA:
				return fileMatcher.matcher(key).group(2);
			case FILE_METADATA:
				return fileMetaDataMatcher.matcher(key).group(2);
			case BYTECODE:
				return byteCodeMatcher.matcher(key).group(2);
			case SYSTEM_DELETION_TIME:
				return systemDeleteMatcher.matcher(key).group(2);
			default:
				throw new IllegalArgumentException("Invalid Blob type");
		}
	}
}
