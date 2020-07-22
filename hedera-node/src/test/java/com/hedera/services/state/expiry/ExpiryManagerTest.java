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

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.state.serdes.DomainSerdesTest;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class ExpiryManagerTest {
	long now = 1_234_567L;
	AccountID payer = IdUtils.asAccount("0.0.13257");
	TransactionRecord record = DomainSerdesTest.recordOne().asGrpc();

	//	verify(properties, times(1)).getAccountProperty("ledger.funding.account");
//	verify(ledger).doTransfer(b, funding, recordFee);
//	verify(ledger, never()).doTransfer(c, funding, recordFee);
//	verify(ledger).doTransfer(d, funding, recordFee);
//	// and:
//	verify(properties).getIntProperty("ledger.records.ttl");
	PropertySource properties;
	MonotonicFullQueueExpiries<Long> payerExpiries, thresholdExpiries;

	ExpiryManager subject;

	@BeforeEach
	public void setup() {
		properties = mock(PropertySource.class);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(180);
		given(properties.getIntProperty("ledger.records.ttl")).willReturn(25 * 60 * 60);

		subject = new ExpiryManager(properties);
	}

	@Test
	public void addsExpectedExpiryForPayer() {
		// setup:
		subject.payerExpiries = (MonotonicFullQueueExpiries<Long>)mock(MonotonicFullQueueExpiries.class);

		// when:
		subject.trackPayerRecord(record, payer, now);

		// then:
		verify(subject.payerExpiries).track(Long.valueOf(13257), now + 180);
	}
}