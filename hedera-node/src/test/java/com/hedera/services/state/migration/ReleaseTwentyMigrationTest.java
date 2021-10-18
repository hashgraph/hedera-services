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
import com.swirlds.common.CommonUtils;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.ReleaseTwentyMigration.migrateFromBinaryObjectStore;
import static com.hedera.services.state.migration.StateChildIndices.CONTRACT_STORAGE;
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
	private final byte[] expiryTimeBlob = "expiryTime".getBytes();

	private final BlobKey dataKey = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
	private final BlobKey metadataKey = new BlobKey(BlobKey.BlobType.FILE_METADATA, 3);
	private final BlobKey bytecodeKey = new BlobKey(BlobKey.BlobType.CONTRACT_BYTECODE, 4);
	private final BlobKey expiryTimeKey = new BlobKey(BlobKey.BlobType.SYSTEM_DELETED_ENTITY_EXPIRY, 6);

	private final String[] contract5Keys = {
			"e898111911e68c7ce0f413c269e1f108b76a0cfb828081a0e98f2bb97edec352",
			"bda7af15ed09711b3ea8f27acfe1a1224787b017df37440a0a8f4ac02856073c"
	};
	private final String[] contract5Values = {
			"b190a4ce23a5520349f35d8e6fe647a71fc409e00d5f7fecbf9bf8e86a721cc0",
			"66719436e82f0e4541e146ae9705f463b12dd8ef9dee6b6aa2b928883d9afff3"
	};
	private final String contract5Storage = "bda7af15ed09711b3ea8f27acfe1a1224787b017df37440a0a8f4ac02856073c6671943" +
			"6e82f0e4541e146ae9705f463b12dd8ef9dee6b6aa2b928883d9afff3e898111911e68c7ce0f413c269e1f108b76a0cfb828081" +
			"a0e98f2bb97edec352b190a4ce23a5520349f35d8e6fe647a71fc409e00d5f7fecbf9bf8e86a721cc0";

	@BeforeEach
	void setUp() {
		final String dataPath = "/0/f2";
		legacyBlobs.put(dataPath, new MerkleOptionalBlob(dataBlob));
		final String metadataPath = "/0/k3";
		legacyBlobs.put(metadataPath, new MerkleOptionalBlob(metadataBlob));
		final String bytecodePath = "/0/s4";
		legacyBlobs.put(bytecodePath, new MerkleOptionalBlob(bytecodeBlob));
		final String storagePath = "/0/d5";
		legacyBlobs.put(storagePath, new MerkleOptionalBlob(CommonUtils.unhex(contract5Storage)));
		final String expiryTimePath = "/0/e6";
		legacyBlobs.put(expiryTimePath, new MerkleOptionalBlob(expiryTimeBlob));
	}

	@Test
	void migratesToStandInVMapsAsExpected() {
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<MerkleMap<BlobKey, MerkleBlob>> blobCaptor =
				forClass(MerkleMap.class);
		@SuppressWarnings("unchecked")
		final ArgumentCaptor<MerkleMap<ContractStorageKey, MerkleContractStorageValue>> storageCaptor =
				forClass(MerkleMap.class);

		given(state.getChild(STORAGE)).willReturn(legacyBlobs);

		migrateFromBinaryObjectStore(state, StateVersions.RELEASE_0190_VERSION);

		verify(state).setChild(eq(STORAGE), blobCaptor.capture());
		verify(state).setChild(eq(CONTRACT_STORAGE), storageCaptor.capture());

		final var vmBlobsStandIn = blobCaptor.getValue();
		final var vmStorageStandIn = storageCaptor.getValue();

		assertArrayEquals(dataBlob, vmBlobsStandIn.get(dataKey).getData());
		assertArrayEquals(metadataBlob, vmBlobsStandIn.get(metadataKey).getData());
		assertArrayEquals(bytecodeBlob, vmBlobsStandIn.get(bytecodeKey).getData());
		assertArrayEquals(expiryTimeBlob, vmBlobsStandIn.get(expiryTimeKey).getData());

		for (int i = 0; i < contract5Keys.length; i++) {
			final var key = contract5Keys[i];
			final var mapKey = new ContractStorageKey(5, CommonUtils.unhex(key));
			final var mapValue = vmStorageStandIn.get(mapKey);
			assertArrayEquals(CommonUtils.unhex(contract5Values[i]), mapValue.getValue());
		}
	}
}
