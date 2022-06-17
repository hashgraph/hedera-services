package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.ClassLoaderHelper;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.crypto.engine.CryptoEngine;
import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.fchashmap.FCHashMap;
import com.swirlds.platform.SignedStateFileManager;
import com.swirlds.platform.state.DualStateImpl;
import com.swirlds.platform.state.SignedState;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.hedera.services.ServicesState.EMPTY_HASH;
import static com.hedera.services.context.AppsManager.APPS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * These tests are responsible for testing loading of signed state data generated for various scenarios from various
 * tagged versions of the code.
 *
 * NOTE: If you see a failure in these tests, it means a change was made to the de-serialization path causing the load to
 * fail. Please double-check that a change made to the de-serialization code path is not adversely affecting decoding of
 * previous saved serialized byte data. Also, make sure that you have fully read out all bytes to de-serialize and not
 * leaving remaining bytes in the stream to decode.
 */
public class ServicesStateE2ETest {
	private final String signedStateDir = "src/test/resources/signedState/";

	@BeforeAll
	public static void setUp() {
		ClassLoaderHelper.loadClassPathDependencies();
	}

	@Test
	void testNftsFromSignedStateV24() {
		assertDoesNotThrow(() -> loadSignedState(signedStateDir + "v0.24.2-nfts/SignedState.swh"));
	}

	@Test
	void testMigrationFromSignedStateV24() {
		assertDoesNotThrow(() -> migrate(signedStateDir + "v0.24.2-nfts/SignedState.swh"));
	}

	@Test
	void testGenesisState() {
		final var swirldDualState = new DualStateImpl();
		final var servicesState = new ServicesState();
		final var recordsRunningHashLeaf = new RecordsRunningHashLeaf();
		recordsRunningHashLeaf.setRunningHash(new RunningHash(EMPTY_HASH));
		servicesState.setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
		final var platform = createMockPlatform();
		final var nodeId = platform.getSelfId().getId();
		final var address = new Address(
				nodeId, "", "", 1L, false, null, -1, null, -1, null, -1, null, -1,
				null, null, (SerializablePublicKey) null, "");
		final var addressBook = new AddressBook(List.of(address));
		final var app = createApp(platform);

		APPS.save(platform.getSelfId().getId(), app);
		assertDoesNotThrow(() -> servicesState.init(
				platform,
				addressBook,
				swirldDualState,
				InitTrigger.GENESIS,
				null));
	}

	private static Hash migrate(String dataPath) throws IOException, InterruptedException {
		final var signedState = loadSignedState(dataPath);
		final var addressBook = signedState.getAddressBook();
		final var swirldDualState = signedState.getState().getSwirldDualState();

		final var platform = createMockPlatform();
		final var servicesState = (ServicesState) signedState.getSwirldState();
		servicesState.init(platform, addressBook, swirldDualState, InitTrigger.RESTART, null);
		servicesState.setMetadata(new StateMetadata(createApp(platform), new FCHashMap<>()));
		return servicesState.runningHashLeaf().currentRunningHash();
	}

	private static ServicesApp createApp(Platform platform) {
		return DaggerServicesApp.builder()
				.initialHash(new Hash())
				.platform(platform)
				.selfId(platform.getSelfId().getId())
				.staticAccountMemo("memo")
				.bootstrapProps(new BootstrapProperties())
				.build();
	}

	private static Platform createMockPlatform() {
		final var platform = mock(Platform.class);
		when(platform.getSelfId()).thenReturn(new NodeId(false, 0));
		when(platform.getCryptography()).thenReturn(new CryptoEngine());
		return platform;
	}

	private static SignedState loadSignedState(final String path) throws IOException {
		final var signedPair = SignedStateFileManager.readSignedStateFromFile(new File(path));
		// Because it's possible we are loading old data, we cannot check equivalence of the hash.
		Assertions.assertNotNull(signedPair.signedState());
		return signedPair.signedState();
	}
}
