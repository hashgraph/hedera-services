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

	GlobalDynamicProperties subject;

	@BeforeEach
	public void setup() {
		properties = mock(PropertySource.class);
	}

	@Test
	public void constructsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new GlobalDynamicProperties(properties);

		// expect:
		assertFalse(subject.shouldCreateThresholdRecords());
		assertEquals(1, subject.maxTokensPerAccount());
		assertEquals(2, subject.maxTokenSymbolLength());
		assertEquals(3L, subject.maxAccountNum());
		assertEquals(4L, subject.defaultContractSendThreshold());
		assertEquals(5L, subject.defaultContractReceiveThreshold());
	}

	@Test
	public void reloadWorksAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new GlobalDynamicProperties(properties);

		// expect:
		assertTrue(subject.shouldCreateThresholdRecords());
		assertEquals(2, subject.maxTokensPerAccount());
		assertEquals(3, subject.maxTokenSymbolLength());
		assertEquals(4L, subject.maxAccountNum());
		assertEquals(5L, subject.defaultContractSendThreshold());
		assertEquals(6L, subject.defaultContractReceiveThreshold());
	}

	private void givenPropsWithSeed(int i) {
		given(properties.getIntProperty("tokens.maxPerAccount")).willReturn(i);
		given(properties.getIntProperty("tokens.maxSymbolLength")).willReturn(i + 1);
		given(properties.getBooleanProperty("ledger.createThresholdRecords")).willReturn((i % 2) == 0);
		given(properties.getLongProperty("ledger.maxAccountNum")).willReturn((long)i + 2);
		given(properties.getLongProperty("contracts.defaultSendThreshold")).willReturn((long)i + 3);
		given(properties.getLongProperty("contracts.defaultReceiveThreshold")).willReturn((long)i + 4);
	}
}