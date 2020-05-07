package com.hedera.services.context;

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

import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.exceptions.ContextNotFoundException;
import com.hedera.services.legacy.services.context.primitives.ExchangeRateSetWrapper;
import com.hedera.services.legacy.services.context.primitives.SequenceNumber;
import com.swirlds.common.AddressBook;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
public class SingletonContextsManagerTest {
	private final long VERSION = 123L;
	private final NodeId id = new NodeId(false, 1L);

	Platform platform;
	PropertySources propertySources;

	@BeforeEach
	private void resetContexts() {
		CONTEXTS.clear();
		platform = mock(Platform.class);
		propertySources = mock(PropertySources.class);
	}

	@Test
	public void failsFastOnMissingContext() {
		// expect:
		assertThrows(ContextNotFoundException.class, () -> CONTEXTS.lookup(1L));
	}

	@Test
	public void createsExpectedLoFiContext() {
		// given:
		AddressBook book = mock(AddressBook.class);
		// and:
		assertFalse(CONTEXTS.isInitialized(1L));

		// when:
		CONTEXTS.store(new HederaNodeContext(id, platform, propertySources, new PrimitiveContext(book)));
		HederaNodeContext ctx = CONTEXTS.lookup(1L);

		// then:
		assertEquals(0L, ctx.versionAtStateInit());
		assertEquals(book, ctx.addressBook());
		assertNull(ctx.consensusTimeOfLastHandledTxn());
		assertNotNull(ctx.seqNo());
		assertNotNull(ctx.midnightRates());
		assertNotNull(ctx.accounts());
		assertNotNull(ctx.storage());
		assertNotNull(ctx.topics());
		// and:
		assertTrue(CONTEXTS.isInitialized(1L));
	}

	@Test
	public void createsExpectedMidFiContext() {
		// given:
		AddressBook book = mock(AddressBook.class);
		FCMap storage = mock(FCMap.class);
		FCMap accounts = mock(FCMap.class);
		FCMap topics = mock(FCMap.class);
		SequenceNumber seqNo = mock(SequenceNumber.class);

		// when:
		CONTEXTS.store(new HederaNodeContext(
				id,
				platform,
				propertySources,
				new PrimitiveContext(VERSION, book, seqNo, accounts, storage, topics))
		);
		HederaNodeContext ctx = CONTEXTS.lookup(1L);

		// then:
		assertEquals(VERSION, ctx.versionAtStateInit());
		assertEquals(book, ctx.addressBook());
		assertNull(ctx.consensusTimeOfLastHandledTxn());
		assertEquals(seqNo, ctx.seqNo());
		assertNotNull(ctx.midnightRates());
		assertEquals(accounts, ctx.accounts());
		assertEquals(storage, ctx.storage());
		assertEquals(topics, ctx.topics());
	}

	@Test
	public void createsExpectedHiFiContext() {
		// given:
		FCMap topics = mock(FCMap.class);
		FCMap storage = mock(FCMap.class);
		FCMap accounts = mock(FCMap.class);
		Instant consensusTimeOfLastHandledTxn = mock(Instant.class);
		AddressBook book = mock(AddressBook.class);
		SequenceNumber seqNo = mock(SequenceNumber.class);
		ExchangeRateSetWrapper exchangeRateSets = mock(ExchangeRateSetWrapper.class);

		// when:
		CONTEXTS.store(new HederaNodeContext(
				id,
				platform,
				propertySources,
				new PrimitiveContext(
						VERSION, consensusTimeOfLastHandledTxn, book, seqNo, exchangeRateSets, accounts, storage, topics)
		));
		HederaNodeContext ctx = CONTEXTS.lookup(1L);

		// then:
		assertEquals(VERSION, ctx.versionAtStateInit());
		assertEquals(book, ctx.addressBook());
		assertEquals(consensusTimeOfLastHandledTxn, ctx.consensusTimeOfLastHandledTxn());
		assertEquals(seqNo, ctx.seqNo());
		assertEquals(exchangeRateSets, ctx.midnightRates());
		assertEquals(accounts, ctx.accounts());
		assertEquals(storage, ctx.storage());
		assertEquals(topics, ctx.topics());
	}
}
