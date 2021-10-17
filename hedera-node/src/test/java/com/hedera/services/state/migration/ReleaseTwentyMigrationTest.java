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
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.ReleaseTwentyMigration.migrateFromBinaryObjectStore;
import static com.hedera.services.state.migration.StateChildIndices.STORAGE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyMigrationTest {
	@Mock
	private ServicesState state;

	private final MerkleMap<String, MerkleOptionalBlob> legacyBlobs = new MerkleMap<>();

	private final byte[] dataBlob = "data".getBytes();
	private final byte[] metadataBlob = "metadata".getBytes();
	private final byte[] bytecodeBlob = "bytecode".getBytes();
	private final byte[] storageBlob = "storage".getBytes();
	private final byte[] expiryTimeBlob = "expiryTime".getBytes();

	private final BlobKey dataKey = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
	private final BlobKey metadataKey = new BlobKey(BlobKey.BlobType.FILE_METADATA, 3);
	private final BlobKey bytecodeKey = new BlobKey(BlobKey.BlobType.CONTRACT_BYTECODE, 4);
	private final BlobKey storageKey = new BlobKey(BlobKey.BlobType.CONTRACT_STORAGE, 5);
	private final BlobKey expiryTimeKey = new BlobKey(BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY, 6);

	@BeforeEach
	void setUp() {
		final String dataPath = "/0/f2";
		legacyBlobs.put(dataPath, new MerkleOptionalBlob(dataBlob));
		final String metadataPath = "/0/k3";
		legacyBlobs.put(metadataPath, new MerkleOptionalBlob(metadataBlob));
		final String bytecodePath = "/0/s4";
		legacyBlobs.put(bytecodePath, new MerkleOptionalBlob(bytecodeBlob));
		final String storagePath = "/0/d5";
		legacyBlobs.put(storagePath, new MerkleOptionalBlob(storageBlob));
		final String expiryTimePath = "/0/e6";
		legacyBlobs.put(expiryTimePath, new MerkleOptionalBlob(expiryTimeBlob));
	}

	@Test
	void replaceBlobStorageMapAsExpected() {
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<MerkleMap<BlobKey, MerkleBlob>> captor = forClass(MerkleMap.class);

		given(state.getChild(STORAGE)).willReturn(legacyBlobs);

		migrateFromBinaryObjectStore(state, StateVersions.RELEASE_0190_VERSION);

		verify(state).setChild(eq(STORAGE), captor.capture());

		final var vmBlobsStandin = captor.getValue();

		assertArrayEquals(dataBlob, vmBlobsStandin.get(dataKey).getData());
		assertArrayEquals(metadataBlob, vmBlobsStandin.get(metadataKey).getData());
		assertArrayEquals(bytecodeBlob, vmBlobsStandin.get(bytecodeKey).getData());
		assertArrayEquals(storageBlob, vmBlobsStandin.get(storageKey).getData());
		assertArrayEquals(expiryTimeBlob, vmBlobsStandin.get(expiryTimeKey).getData());
	}
}
