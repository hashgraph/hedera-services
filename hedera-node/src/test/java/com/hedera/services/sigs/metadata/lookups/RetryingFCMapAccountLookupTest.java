package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.sigs.order.KeyOrderingFailure;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SleepingPause;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.factories.accounts.MerkleAccountFactory.*;

public class RetryingFCMapAccountLookupTest {
	private NodeLocalProperties properties;
	private MiscRunningAvgs runningAvgs;
	private MiscSpeedometers speedometers;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private RetryingFCMapAccountLookup subject;
	private Pause pause;
	private final Pause defaultPause = SleepingPause.SLEEPING_PAUSE;
	private final AccountID account = IdUtils.asAccount("0.0.1337");
	private final MerkleEntityId accountKey = MerkleEntityId.fromAccountId(account);
	private final MerkleAccount accountValue = newAccount().receiverSigRequired(true).accountKeys(accountKeys).get();
	private static JKey accountKeys;
	private static final int RETRY_WAIT_MS = 10;

	@BeforeAll
	private static void setupAll() throws Throwable {
		accountKeys = KeyTree.withRoot(ed25519()).asJKey();
	}

	@BeforeEach
	private void setup() {
		runningAvgs = mock(MiscRunningAvgs.class);
		speedometers = mock(MiscSpeedometers.class);
		pause = mock(Pause.class);
		accounts = (FCMap<MerkleEntityId, MerkleAccount>)mock(FCMap.class);
		properties = mock(NodeLocalProperties.class);
		given(properties.precheckLookupRetries()).willReturn(2);
		given(properties.precheckLookupRetryBackoffMs()).willReturn(RETRY_WAIT_MS);
	}

	@Test
	public void neverRetriesIfAccountAlreadyExists() throws Exception {
		given(accounts.get(accountKey)).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, () -> accounts, runningAvgs, speedometers);

		// when:
		AccountSigningMetadata meta = subject.safeLookup(account).metadata();

		// then:
		verifyZeroInteractions(pause);
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesTwiceWithStats() throws Exception {
		given(pause.forMs(anyLong())).willReturn(true);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, () -> accounts, runningAvgs, speedometers);
		// and:
		InOrder inOrder = inOrder(pause, speedometers, runningAvgs);

		// when:
		AccountSigningMetadata meta = subject.safeLookup(account).metadata();

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(speedometers).cycleAccountLookupRetries();
		inOrder.verify(runningAvgs).recordAccountLookupRetries(captor.capture());
		inOrder.verify(runningAvgs).recordAccountRetryWaitMs(anyDouble());
		assertEquals(2, captor.getValue().intValue());
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesOnceWithSleepingPause() throws Exception {
		given(accounts.get(accountKey)).willReturn(null).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(defaultPause, properties, () -> accounts, runningAvgs, speedometers);
		// and:
		InOrder inOrder = inOrder(runningAvgs, speedometers);

		// when:
		AccountSigningMetadata meta = subject.safeLookup(account).metadata();

		// then:
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(speedometers).cycleAccountLookupRetries();
		inOrder.verify(runningAvgs).recordAccountLookupRetries(captor.capture());
		inOrder.verify(runningAvgs).recordAccountRetryWaitMs(anyDouble());
		assertEquals(1, captor.getValue().intValue());
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesTwiceAndAbortsOnFailure() {
		given(pause.forMs(anyLong())).willReturn(true);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(null);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, () -> accounts, runningAvgs, speedometers);
		// and:
		InOrder inOrder = inOrder(pause, runningAvgs, speedometers);

		// when:
		assertEquals(KeyOrderingFailure.MISSING_ACCOUNT, subject.safeLookup(account).failureIfAny());

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(speedometers).cycleAccountLookupRetries();
		inOrder.verify(runningAvgs).recordAccountLookupRetries(captor.capture());
		inOrder.verify(runningAvgs).recordAccountRetryWaitMs(anyDouble());
		assertEquals(2, captor.getValue().intValue());
	}

	@Test
	public void abortsIfPauseFails() {
		given(pause.forMs(anyLong())).willReturn(true).willReturn(false);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(null);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, () -> accounts, runningAvgs, speedometers);
		// and:
		InOrder inOrder = inOrder(pause);

		// when:
		assertEquals(KeyOrderingFailure.MISSING_ACCOUNT, subject.safeLookup(account).failureIfAny());

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
	}
}
