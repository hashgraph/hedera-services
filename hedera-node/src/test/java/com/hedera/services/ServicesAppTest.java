package com.hedera.services;

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

import com.hedera.services.context.init.FullInitializationFlow;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.DualStateAccessor;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ServicesAppTest {
	private long selfId = 123;
	private NodeId selfNodeId = new NodeId(false, selfId);
	private ServicesApp subject;

	@Mock
	private Platform platform;
	@Mock
	private AddressBook addressBook;
	@Mock
	private Cryptography cryptography;
	@Mock
	private ServicesState initialState;
	@Mock
	private Hash hash;
	@Mock
	private RunningHash runningHash;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;

	@BeforeEach
	void setUp() {
		given(initialState.addressBook()).willReturn(addressBook);
		given(initialState.runningHashLeaf()).willReturn(runningHashLeaf);
		given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
		given(runningHash.getHash()).willReturn(hash);
//		given(platform.getCryptography()).willReturn(cryptography);
		given(platform.getSelfId()).willReturn(selfNodeId);

		subject = DaggerServicesApp.builder()
				.initialState(initialState)
				.platform(platform)
				.selfId(selfId)
				.build();
	}

	@Test
	void objectGraphRootsAreAvailable() {
		// expect:
		assertThat(subject.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
		assertThat(subject.globalDynamicProperties(), instanceOf(GlobalDynamicProperties.class));
		assertThat(subject.recordStreamManager(), instanceOf(RecordStreamManager.class));
		assertThat(subject.initializationFlow(), instanceOf(FullInitializationFlow.class));
		assertThat(subject.dualStateAccessor(), instanceOf(DualStateAccessor.class));
	}
}
