package com.hedera.services.ledger;

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

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.stream.Collectors;

import static com.hedera.services.ledger.properties.AccountProperty.HISTORY_RECORDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
public class HistoryRecordsPurgeTest extends BaseHederaLedgerTest {
	@BeforeEach
	void setup() {
		commonSetup();
		setupWithMockLedger();
	}

	@Test
	public void purgesExpiredRecords() {
		// setup:
		FCQueue<ExpirableTxnRecord> records = asExpirableRecords(50L, 100L, 200L, 311L, 500L);
		addRecords(misc, records);

		// when:
		long newEarliestExpiry = subject.purgeExpiredRecords(misc, 200L);

		// then:
		assertEquals(311L, newEarliestExpiry);
		ArgumentCaptor<FCQueue> captor = ArgumentCaptor.forClass(FCQueue.class);
		verify(accountsLedger).set(
				argThat(misc::equals),
				argThat(HISTORY_RECORDS::equals),
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
