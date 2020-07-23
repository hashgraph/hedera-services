package com.hedera.services.state.expiry;

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

import com.hedera.services.context.ServicesContext;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class ExpiryManagerTest {
	long a = 13257, b = 75231;
	long[] aHistorical = { 10, 20, 20, 20 };
	long[] bHistorical = { 10, 50 };
	long[] aPayer = { 55 };
	long[] bPayer = { 33 };

	long expiry = 1_234_567L;
	AccountID payer = IdUtils.asAccount("0.0.13257");

	HederaLedger ledger;
	ServicesContext ctx;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	ExpiryManager subject;

	@BeforeEach
	public void setup() {
		accounts = new FCMap<>();
		ledger = mock(HederaLedger.class);
		ctx = mock(ServicesContext.class);

		given(ctx.ledger()).willReturn(ledger);
		given(ctx.accounts()).willReturn(accounts);

		subject = new ExpiryManager();
	}

	@Test
	public void purgesAsExpected() {
		// setup:
		InOrder inOrder = inOrder(ledger);

		givenAccount(a, aHistorical, aPayer);
		givenAccount(b, bHistorical, bPayer);
		// given:
		subject.resumeTrackingFrom(ctx);

		// when:
		subject.purgeExpiriesAt(33, ctx);

		// then:
		inOrder.verify(ledger).purgeExpiredRecords(asAccount(a), 33);
		inOrder.verify(ledger).purgeExpiredRecords(asAccount(b), 33);
		inOrder.verify(ledger).purgeExpiredRecords(asAccount(a), 33);
		// and:
		inOrder.verify(ledger).purgeExpiredPayerRecords(asAccount(b), 33);
		// and:
		System.out.println("Final payerExpiries: " + subject.payerExpiries.allExpiries);
		System.out.println("Final historicalExpiries: " + subject.historicalExpiries.allExpiries);
	}

	private AccountID asAccount(long num) {
		return IdUtils.asAccount(String.format("0.0.%d",  num));
	}

	@Test
	public void resumesTrackingAsExpected() {
		givenAccount(a, aHistorical, aPayer);
		givenAccount(b, bHistorical, bPayer);

		// when:
		subject.resumeTrackingFrom(ctx);

		// then:
		var e1 = subject.payerExpiries.allExpiries.poll();
		assertEquals(b, e1.getId());
		assertEquals(33, e1.getExpiry());
		var e2 = subject.payerExpiries.allExpiries.poll();
		assertEquals(a, e2.getId());
		assertEquals(55, e2.getExpiry());
		// and:
		assertTrue(subject.payerExpiries.allExpiries.isEmpty());
		// and:
		var e3 = subject.historicalExpiries.allExpiries.poll();
		assertEquals(a, e3.getId());
		assertEquals(10, e3.getExpiry());
		var e4 = subject.historicalExpiries.allExpiries.poll();
		assertEquals(b, e4.getId());
		assertEquals(10, e4.getExpiry());
		var e5 = subject.historicalExpiries.allExpiries.poll();
		assertEquals(a, e5.getId());
		assertEquals(20, e5.getExpiry());
		var e6 = subject.historicalExpiries.allExpiries.poll();
		assertEquals(b, e6.getId());
		assertEquals(50, e6.getExpiry());
	}

	private void givenAccount(long num, long[] historicalExpiries, long[] payerExpiries) {
		var account = new MerkleAccount();
		for (long t : payerExpiries) {
			account.payerRecords().offer(withExpiry(t));
		}
		for (long t : historicalExpiries) {
			account.records().offer(withExpiry(t));
		}
		var id = new MerkleEntityId(0, 0, num);
		accounts.put(id, account);
	}

	private ExpirableTxnRecord withExpiry(long t) {
		var r = new ExpirableTxnRecord();
		r.setExpiry(t);
		return r;
	}

	@Test
	public void addsExpectedExpiryForPayer() {
		// setup:
		subject.payerExpiries = (MonotonicFullQueueExpiries<Long>)mock(MonotonicFullQueueExpiries.class);

		// when:
		subject.trackPayerRecord(payer, expiry);

		// then:
		verify(subject.payerExpiries).track(Long.valueOf(13257), expiry);
	}

	@Test
	public void addsExpectedExpiryForThreshold() {
		// setup:
		subject.historicalExpiries = (MonotonicFullQueueExpiries<Long>)mock(MonotonicFullQueueExpiries.class);

		// when:
		subject.trackHistoricalRecord(payer, expiry);

		// then:
		verify(subject.historicalExpiries).track(Long.valueOf(13257), expiry);
	}
}