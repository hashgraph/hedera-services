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

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.init.ServicesInitFlow;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.state.forensics.HashLogger;
import com.hedera.services.state.migration.StateChildIndices;
import com.hedera.services.state.org.StateMetadata;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.test.utils.ClassLoaderHelper;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.crypto.SerializablePublicKey;
import com.swirlds.common.system.Address;
import com.swirlds.common.system.AddressBook;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldDualState;
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
	void testMigrationFromSignedStateV24() throws IOException {
		SignedState signedState = loadSignedState(signedStateDir + "v0.24.2-nfts/SignedState.swh");
		AddressBook addressBook = signedState.getAddressBook();
		SwirldDualState swirldDualState = signedState.getState().getSwirldDualState();

		Platform platform = createMockPlatform();
		ServicesState servicesState = (ServicesState) signedState.getSwirldState();
		servicesState.init(platform, addressBook, swirldDualState);
		servicesState.setMetadata(new StateMetadata(createMockApp(), new FCHashMap<>()));
		servicesState.migrate();
	}

	@Test
	void testGenesisState() {
		SwirldDualState swirldDualState = new DualStateImpl();
		ServicesState servicesState = new ServicesState();
		RecordsRunningHashLeaf recordsRunningHashLeaf = new RecordsRunningHashLeaf();
		recordsRunningHashLeaf.setRunningHash(new RunningHash(new Hash()));
		servicesState.setChild(StateChildIndices.RECORD_STREAM_RUNNING_HASH, recordsRunningHashLeaf);
		Platform platform = createMockPlatform();
		long nodeId = platform.getSelfId().getId();
		Address address;
		address = new Address(
				nodeId, "", "", 1L, false, null, -1, null, -1, null, -1, null, -1,
				null, null, (SerializablePublicKey)null, "");
		AddressBook addressBook = new AddressBook(List.of(address));
		ServicesApp mockApp = createMockApp();

		APPS.save(platform.getSelfId().getId(), mockApp);
		assertDoesNotThrow(() -> servicesState.genesisInit(platform, addressBook, swirldDualState));
	}


	private static ServicesApp createMockApp() {
		ServicesApp mockApp = mock(ServicesApp.class);
		when(mockApp.dualStateAccessor()).thenReturn(new DualStateAccessor());
		when(mockApp.initializationFlow()).thenReturn(mock(ServicesInitFlow.class));
		when(mockApp.hashLogger()).thenReturn(new HashLogger());
		when(mockApp.workingState()).thenReturn(new MutableStateChildren());
		return mockApp;
	}
	private static Platform createMockPlatform() {
		Platform platform = mock(Platform.class);
		when(platform.getSelfId()).thenReturn(new NodeId(false, 0));
		return platform;
	}

	private static SignedState loadSignedState(final String path) throws IOException {
		var signedPair = SignedStateFileManager.readSignedStateFromFile(new File(path));
		// Because it's possible we are loading old data, we cannot check equivalence of the hash.
		Assertions.assertNotNull(signedPair.getRight());
		return signedPair.getRight();
	}
}
