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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BlobKey;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.AddressBook;
import com.swirlds.common.merkle.copy.MerkleCopy;
import com.swirlds.merkle.map.MerkleMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static com.hedera.services.state.migration.Release0170Migration.moveLargeFcmsToBinaryRoutePositions;
import static com.hedera.services.state.migration.ReleaseTwentyMigration.replaceStorageMapWithVirtualMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentyMigrationTest {
	@Mock
	private ServicesState state;

	private MerkleMap<String, MerkleOptionalBlob> legacyMap = new MerkleMap<>();
	private MerkleMap<BlobKey, MerkleBlob> virtualMap = new MerkleMap<>();

	@BeforeEach
	void setUp(){
		final String pathA = "/0/f2";
		final String pathB = "/0/k3";
		final String pathC = "/0/s4";
		final String pathD = "/0/e5";
		legacyMap.put(pathA, new MerkleOptionalBlob("blobA".getBytes()));
		legacyMap.put(pathB, new MerkleOptionalBlob("blobB".getBytes()));
		legacyMap.put(pathC, new MerkleOptionalBlob("blobC".getBytes()));
		legacyMap.put(pathD, new MerkleOptionalBlob("blobD".getBytes()));
	}

	@Test
	void replaceBlobStorageMapAsExpected() {
		given(state.getChild(StateChildIndices.STORAGE)).willReturn(legacyMap);

		replaceStorageMapWithVirtualMap(state, StateVersions.RELEASE_0190_VERSION);

		virtualMap = state.getChild(StateChildIndices.STORAGE);

	}
}
