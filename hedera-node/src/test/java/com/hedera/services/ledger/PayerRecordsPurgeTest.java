package com.hedera.services.ledger;

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

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.hedera.services.ledger.properties.AccountProperty.RECORDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

public class PayerRecordsPurgeTest extends BaseHederaLedgerTest {
	@BeforeEach
	void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void purgesExpiredPayerRecords() {
		// setup:
		Consumer<ExpirableTxnRecord> cb = (Consumer<ExpirableTxnRecord>) mock(Consumer.class);
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(50L, 100L, 200L, 311L, 500L);
		List<ExpirableTxnRecord> added = new ArrayList<>(records);
		addPayerRecords(misc, records);

		// when:
		long newEarliestExpiry = subject.purgeExpiredRecords(misc, 200L, cb);

		// then:
		assertEquals(311L, newEarliestExpiry);
		// and:
		verify(cb).accept(same(added.get(0)));
		verify(cb).accept(same(added.get(1)));
		verify(cb).accept(same(added.get(2)));
		// and:
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(RECORDS::equals),
				captor.capture());
		// and:
		assertTrue(captor.getValue() == records);
		assertThat(
				((FCQueue<ExpirableTxnRecord>) captor.getValue())
						.stream()
						.map(ExpirableTxnRecord::getExpiry)
						.collect(Collectors.toList()),
				contains(311L, 500L));
	}
}
