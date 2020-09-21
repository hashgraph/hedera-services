package com.hedera.services.context.properties;

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

import com.hedera.services.config.HederaNumbers;
import com.hederahashgraph.api.proto.java.AccountID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class GlobalDynamicPropertiesTest {
	PropertySource properties;

	HederaNumbers numbers;
	GlobalDynamicProperties subject;

	@BeforeEach
	public void setup() {
		numbers = mock(HederaNumbers.class);
		given(numbers.shard()).willReturn(1L);
		given(numbers.realm()).willReturn(2L);
		properties = mock(PropertySource.class);
	}

	@Test
	public void constructsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// expect:
		assertFalse(subject.shouldCreateThresholdRecords());
		assertEquals(1, subject.maxTokensPerAccount());
		assertEquals(2, subject.maxTokenSymbolLength());
		assertEquals(3L, subject.maxAccountNum());
		assertEquals(4L, subject.defaultContractSendThreshold());
		assertEquals(5L, subject.defaultContractReceiveThreshold());
		assertEquals(6, subject.maxFileSizeKb());
		assertEquals(accountWith(1L, 2L, 7L), subject.fundingAccount());
		assertEquals(8, subject.cacheRecordsTtl());
		assertEquals(9, subject.maxContractStorageKb());
		assertEquals(10, subject.ratesIntradayChangeLimitPercent());
	}

	private AccountID accountWith(long shard, long realm, long num) {
		return AccountID.newBuilder()
				.setShardNum(shard)
				.setRealmNum(realm)
				.setAccountNum(num)
				.build();
	}

	@Test
	public void reloadWorksAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(numbers, properties);

		// expect:
		assertTrue(subject.shouldCreateThresholdRecords());
		assertEquals(2, subject.maxTokensPerAccount());
		assertEquals(3, subject.maxTokenSymbolLength());
		assertEquals(4L, subject.maxAccountNum());
		assertEquals(5L, subject.defaultContractSendThreshold());
		assertEquals(6L, subject.defaultContractReceiveThreshold());
		assertEquals(7, subject.maxFileSizeKb());
		assertEquals(accountWith(1L, 2L, 8L), subject.fundingAccount());
		assertEquals(9, subject.cacheRecordsTtl());
		assertEquals(10, subject.maxContractStorageKb());
		assertEquals(11, subject.ratesIntradayChangeLimitPercent());
	}

	private void givenPropsWithSeed(int i) {
		given(properties.getIntProperty("tokens.maxPerAccount")).willReturn(i);
		given(properties.getIntProperty("tokens.maxSymbolLength")).willReturn(i + 1);
		given(properties.getBooleanProperty("ledger.createThresholdRecords")).willReturn((i % 2) == 0);
		given(properties.getLongProperty("ledger.maxAccountNum")).willReturn((long)i + 2);
		given(properties.getLongProperty("contracts.defaultSendThreshold")).willReturn((long)i + 3);
		given(properties.getLongProperty("contracts.defaultReceiveThreshold")).willReturn((long)i + 4);
		given(properties.getIntProperty("files.maxSizeKb")).willReturn(i + 5);
		given(properties.getLongProperty("ledger.fundingAccount")).willReturn((long)i + 6);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(i + 7);
		given(properties.getIntProperty("contracts.maxStorageKb")).willReturn(i + 8);
		given(properties.getIntProperty("rates.intradayChangeLimitPercent")).willReturn(i + 9);
	}
}