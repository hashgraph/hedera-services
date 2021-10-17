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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.state.migration.ReleaseTwentyMigration.replaceStorageMapWithVirtualMap;
import static com.hedera.services.state.migration.StateChildIndices.STORAGE;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyMigrationTest {
	@Mock
	private ServicesState state;

	@Mock
	private MerkleMap<BlobKey, MerkleBlob> storage;

	private MerkleMap<String, MerkleOptionalBlob> legacyMap = new MerkleMap<>();
	private MerkleMap<BlobKey, MerkleBlob> virtualMap = new MerkleMap<>();

	private final String pathA = "/0/f2";
	private final String pathB = "/0/k3";
	private final String pathC = "/0/s4";
	private final String pathD = "/0/e5";
	private final byte[] dataA = "blobA".getBytes();
	private final byte[] dataB = "blobB".getBytes();
	private final byte[] dataC = "blobC".getBytes();
	private final byte[] dataD = "blobD".getBytes();

	@BeforeEach
	void setUp(){
		legacyMap.put(pathA, new MerkleOptionalBlob(dataA));
		legacyMap.put(pathB, new MerkleOptionalBlob(dataB));
		legacyMap.put(pathC, new MerkleOptionalBlob(dataC));
		legacyMap.put(pathD, new MerkleOptionalBlob(dataD));

		BlobKey expectedKeyA = new BlobKey(BlobKey.BlobType.FILE_DATA, 2);
		BlobKey expectedKeyB = new BlobKey(BlobKey.BlobType.FILE_METADATA, 3);
		BlobKey expectedKeyC = new BlobKey(BlobKey.BlobType.BYTECODE, 4);
		BlobKey expectedKeyD = new BlobKey(BlobKey.BlobType.SYSTEM_DELETION_TIME, 5);

		MerkleBlob expectedBlobA = new MerkleBlob(dataA);
		MerkleBlob expectedBlobB = new MerkleBlob(dataB);
		MerkleBlob expectedBlobC = new MerkleBlob(dataC);
		MerkleBlob expectedBlobD = new MerkleBlob(dataD);

		virtualMap.put(expectedKeyA, expectedBlobA);
		virtualMap.put(expectedKeyB, expectedBlobB);
		virtualMap.put(expectedKeyC, expectedBlobC);
		virtualMap.put(expectedKeyD, expectedBlobD);
	}

	@Test
	void replaceBlobStorageMapAsExpected() {
		given(state.getChild(STORAGE)).willReturn(legacyMap);

		replaceStorageMapWithVirtualMap(state, StateVersions.RELEASE_0190_VERSION);


		//verify(state).setChild(STORAGE, virtualMap);
	}
}
