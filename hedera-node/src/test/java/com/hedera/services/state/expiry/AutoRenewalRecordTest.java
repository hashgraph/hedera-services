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

public class AutoRenewalRecordTest {
	@Test
	public void shouldGenerateCorrectAutoRenewalRecord() {
		var accountRenewed = IdUtils.asAccount("1.2.3");
		var autoRenewAccount = IdUtils.asAccount("4.5.6");
		long consensusSeconds = 1_234_567L;
		long consensusNanos = 890L;
		long fee = 9_876_543L;
		long newExpirationTime = 2_345_678L;
		var feeCollector = IdUtils.asAccount("7.8.9");
		var renewedAt = Instant.ofEpochSecond(consensusSeconds).plusNanos(consensusNanos);
		var record = AutoRenewalRecord.generatedFor(accountRenewed, renewedAt, autoRenewAccount,
				fee, newExpirationTime, feeCollector);

		assertEquals(accountRenewed, record.getReceipt().getAccountID());
		assertTrue(record.getTransactionHash().isEmpty());
		assertEquals(consensusSeconds, record.getConsensusTimestamp().getSeconds());
		assertEquals(consensusNanos, record.getConsensusTimestamp().getNanos());
		assertEquals(autoRenewAccount, record.getTransactionID().getAccountID());
		assertFalse(record.getTransactionID().hasTransactionValidStart());
		assertEquals("Entity 1.2.3 was automatically renewed. New expiration time: 2345678.", record.getMemo());
		assertEquals(fee, record.getTransactionFee());
		assertFalse(record.hasContractCallResult());
		assertFalse(record.hasContractCreateResult());
		assertTrue(record.hasTransferList());
		var transferList = record.getTransferList();
		assertEquals(2, transferList.getAccountAmountsCount());
		var payerAmount = transferList.getAccountAmounts(0);
		var payeeAmount = transferList.getAccountAmounts(1);
		assertEquals(autoRenewAccount, payerAmount.getAccountID());
		assertEquals(-1 * fee, payerAmount.getAmount());
		assertEquals(feeCollector, payeeAmount.getAccountID());
		assertEquals(fee, payeeAmount.getAmount());
		assertEquals(0, record.getTokenTransferListsCount());
		assertFalse(record.hasScheduleRef());
	}
}
