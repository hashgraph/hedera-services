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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SleepingPause;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import static com.hedera.test.factories.keys.NodeFactory.ed25519;

import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.BDDMockito.*;
import static com.hedera.test.factories.accounts.MapValueFactory.*;

@RunWith(JUnitPlatform.class)
public class RetryingFCMapAccountLookupTest {
	private PropertySource properties;
	private HederaNodeStats stats;
	private FCMap<MerkleEntityId, MerkleAccount> accounts;
	private RetryingFCMapAccountLookup subject;
	private Pause pause;
	private final Pause defaultPause = SleepingPause.INSTANCE;
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
		stats = mock(HederaNodeStats.class);
		pause = mock(Pause.class);
		accounts = (FCMap<MerkleEntityId, MerkleAccount>)mock(FCMap.class);
		properties = mock(PropertySource.class);
		given(properties.getIntProperty("validation.preConsensus.accountKey.maxLookupRetries"))
				.willReturn(2);
		given(properties.getIntProperty("validation.preConsensus.accountKey.retryBackoffIncrementMs"))
				.willReturn(RETRY_WAIT_MS);
	}

	@Test
	public void neverRetriesIfAccountAlreadyExists() throws Exception {
		given(accounts.get(accountKey)).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, stats, () -> accounts);

		// when:
		AccountSigningMetadata meta = subject.lookup(account);

		// then:
		verifyZeroInteractions(stats, pause);
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesTwiceWithStats() throws Exception {
		given(pause.forMs(anyLong())).willReturn(true);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, stats, () -> accounts);
		// and:
		InOrder inOrder = inOrder(pause, stats);

		// when:
		AccountSigningMetadata meta = subject.lookup(account);

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(stats).lookupRetries(captor.capture(), anyDouble());
		assertEquals(2, captor.getValue().intValue());
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesOnceWithSleepingPause() throws Exception {
		given(accounts.get(accountKey)).willReturn(null).willReturn(accountValue);
		// and:
		subject = new RetryingFCMapAccountLookup(defaultPause, properties, stats, () -> accounts);
		// and:
		InOrder inOrder = inOrder(stats);

		// when:
		AccountSigningMetadata meta = subject.lookup(account);

		// then:
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(stats).lookupRetries(captor.capture(), anyDouble());
		assertEquals(1, captor.getValue().intValue());
		assertTrue(meta.isReceiverSigRequired());
		assertEquals(JKey.mapJKey(accountKeys), JKey.mapJKey(meta.getKey()));
	}

	@Test
	public void retriesTwiceAndAbortsOnFailure() {
		given(pause.forMs(anyLong())).willReturn(true);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(null);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, stats, () -> accounts);
		// and:
		InOrder inOrder = inOrder(pause, stats);

		// when:
		assertThrows(InvalidAccountIDException.class, () -> subject.lookup(account));

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
		ArgumentCaptor<Integer> captor = forClass(Integer.class);
		inOrder.verify(stats).lookupRetries(captor.capture(), anyDouble());
		assertEquals(2, captor.getValue().intValue());
	}

	@Test
	public void abortsIfPauseFails() {
		given(pause.forMs(anyLong())).willReturn(true).willReturn(false);
		given(accounts.get(accountKey)).willReturn(null).willReturn(null).willReturn(null);
		// and:
		subject = new RetryingFCMapAccountLookup(pause, properties, stats, () -> accounts);
		// and:
		InOrder inOrder = inOrder(pause, stats);

		// when:
		assertThrows(InvalidAccountIDException.class, () -> subject.lookup(account));

		// then:
		inOrder.verify(pause).forMs(RETRY_WAIT_MS);
		inOrder.verify(pause).forMs(RETRY_WAIT_MS * 2);
		verifyNoInteractions(stats);
	}
}
