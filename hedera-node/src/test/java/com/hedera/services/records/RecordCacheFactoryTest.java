package com.hedera.services.records;

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

import com.google.common.cache.Cache;
import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Optional;

import static com.hedera.services.utils.SleepingPause.INSTANCE;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class RecordCacheFactoryTest {
	private TransactionID txnIdA = TransactionID.newBuilder()
			.setAccountID(asAccount("0.0.2"))
			.build();
	private TransactionID txnIdB = TransactionID.newBuilder()
			.setAccountID(asAccount("2.2.0"))
			.build();
	private TransactionRecord record = TransactionRecord.getDefaultInstance();

	private PropertySource properties;
	private RecordCacheFactory subject;

	@Test
	public void hasExpectedExpiry() {
		// setup:
		properties = mock(PropertySource.class);
		subject = new RecordCacheFactory(properties);

		given(properties.getIntProperty("cache.records.ttl")).willReturn(1);

		// when:
		Cache<TransactionID, Optional<TransactionRecord>> cache = subject.getRecordCache();
		cache.put(txnIdA, Optional.of(record));

		// then:
		assertEquals(record, cache.getIfPresent(txnIdA).get());
		assertNull(cache.getIfPresent(txnIdB));
		INSTANCE.forMs(500L);
		assertEquals(record, cache.getIfPresent(txnIdA).get());
		INSTANCE.forMs(500L);
		assertNull(cache.getIfPresent(txnIdA));
	}

}
