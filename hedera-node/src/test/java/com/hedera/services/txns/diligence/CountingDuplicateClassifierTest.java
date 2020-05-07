package com.hedera.services.txns.diligence;

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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class CountingDuplicateClassifierTest {
	final private AccountID a = asAccount("0.0.1111");
	final private TransactionID aTxnId = TransactionID.newBuilder().setAccountID(a).build();
	final private AccountID b = asAccount("0.0.2222");
	final private TransactionID bTxnId = TransactionID.newBuilder().setAccountID(b).build();

	int cacheTtl = 180;
	long at = 1_234_567L;
	PropertySource properties;
	Map<TransactionID, Integer>	observedCounts;
	BlockingQueue<DuplicateIdHorizon> horizons;

	CountingDuplicateClassifier subject;

	@BeforeEach
	private void setup() {
		horizons = mock(BlockingQueue.class);
		properties = mock(PropertySource.class);
		observedCounts = new HashMap<>();

		given(properties.getIntProperty("cache.records.ttl"))	.willReturn(cacheTtl);

		subject = new CountingDuplicateClassifier(properties, observedCounts, horizons);
	}

	@Test
	public void offersHorizonAndCreatesFirstObservation() {
		// given:
		ArgumentCaptor<DuplicateIdHorizon> captor = ArgumentCaptor.forClass(DuplicateIdHorizon.class);
		// and:
		DuplicateIdHorizon expected = new DuplicateIdHorizon(at + cacheTtl, aTxnId);

		// when:
		subject.observe(aTxnId, at);

		// then:
		verify(horizons).offer(captor.capture());
		assertEquals(expected, captor.getValue());
		// and:
		assertEquals(new Integer(1), observedCounts.get(aTxnId));
	}

	@Test
	public void offersHorizonAndIncrementsNthObservation() {
		// given:
		ArgumentCaptor<DuplicateIdHorizon> captor = ArgumentCaptor.forClass(DuplicateIdHorizon.class);
		// and:
		DuplicateIdHorizon expected = new DuplicateIdHorizon(at + cacheTtl, aTxnId);
		observedCounts.put(aTxnId, 9);

		// when:
		subject.observe(aTxnId, at);

		// then:
		verify(horizons).offer(captor.capture());
		assertEquals(expected, captor.getValue());
		// and:
		assertEquals(new Integer(10), observedCounts.get(aTxnId));
	}

	@Test
	public void subtractsObservationsFromCountMap() {
		// setup:
		horizons = new PriorityBlockingQueue<>();
		subject = new CountingDuplicateClassifier(properties, observedCounts, horizons);

		// given:
		horizons.offer(new DuplicateIdHorizon(at - 1_234L, aTxnId));
		observedCounts.put(aTxnId, 1);
		// and:
		horizons.offer(new DuplicateIdHorizon(at - 617L, bTxnId));
		horizons.offer(new DuplicateIdHorizon(at + 617L, bTxnId));
		observedCounts.put(bTxnId, 2);

		// when:
		subject.shiftWindow(at);

		// then:
		assertFalse(observedCounts.containsKey(aTxnId));
		assertEquals(new Integer(1), observedCounts.get(bTxnId));
		// and:
		assertEquals(1, horizons.size());
	}

	@Test
	public void detectsDuplicatesAsExpected() {
		// given:
		horizons.offer(new DuplicateIdHorizon(at - 1_234L, aTxnId));
		observedCounts.put(aTxnId, 1);

		// expect:
		assertTrue(subject.isDuplicate(aTxnId));
		assertFalse(subject.isDuplicate(bTxnId));
	}
}
