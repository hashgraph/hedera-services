package com.hedera.services.state.expiry;

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

import com.hedera.test.utils.IdUtils;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EntityRemovalRecordTest {
	@Test
	public void shouldGenerateCorrectEntityRemovalRecord() {
		var accountRemoved = IdUtils.asAccount("1.2.3");
		var autoRenewAccount = IdUtils.asAccount("4.5.6");
		long consensusSeconds = 1_234_567L;
		long consensusNanos = 890L;
		var removedAt = Instant.ofEpochSecond(consensusSeconds).plusNanos(consensusNanos);
		var record = EntityRemovalRecord.generatedFor(accountRemoved, removedAt, autoRenewAccount);

		assertEquals(accountRemoved, record.getReceipt().getAccountID());
		assertTrue(record.getTransactionHash().isEmpty());
		assertEquals(consensusSeconds, record.getConsensusTimestamp().getSeconds());
		assertEquals(consensusNanos, record.getConsensusTimestamp().getNanos());
		assertEquals(autoRenewAccount, record.getTransactionID().getAccountID());
		assertFalse(record.getTransactionID().hasTransactionValidStart());
		assertEquals("Entity 1.2.3 was automatically deleted.", record.getMemo());
		assertEquals(0L, record.getTransactionFee());
		assertFalse(record.hasContractCallResult());
		assertFalse(record.hasContractCreateResult());
		assertFalse(record.hasTransferList());
		assertEquals(0L, record.getTokenTransferListsCount());
		assertFalse(record.hasScheduleRef());
	}
}
