package com.hedera.services.config;

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
import com.hedera.test.utils.IdUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class FileNumbersTest {
	PropertySource properties;
	FileNumbers subject;

	@BeforeEach
	private void setup() {
		properties = mock(PropertySource.class);
		given(properties.getLongProperty("files.addressBook.num")).willReturn(101L);
		given(properties.getLongProperty("files.nodeDetails.num")).willReturn(102L);
		given(properties.getLongProperty("files.applicationProperties.num")).willReturn(121L);
		given(properties.getLongProperty("files.apiPermissions.num")).willReturn(122L);
		given(properties.getLongProperty("files.feeSchedules.num")).willReturn(111L);
		given(properties.getLongProperty("files.exchangeRates.num")).willReturn(112L);
		given(properties.getLongProperty("hedera.lastProtectedEntity.num")).willReturn(1_000L);

		subject = new FileNumbers(properties);
	}

	@Test
	public void hasExpectedNumbers() {
		// expect:
		assertEquals(101, subject.addressBook());
		assertEquals(102, subject.nodeDetails());
		assertEquals(111, subject.feeSchedules());
		assertEquals(112, subject.exchangeRates());
		assertEquals(121, subject.applicationProperties());
		assertEquals(122, subject.apiPermissions());
		assertTrue(subject.isSystem(1_000));
		assertFalse(subject.isSystem(1_001));
	}

	@Test
	public void getsExpectedFid() {
		given(properties.getLongProperty("hedera.shard")).willReturn(1L);
		given(properties.getLongProperty("hedera.realm")).willReturn(2L);

		// when:
		var fid = subject.toFid(3L);

		// then:
		assertEquals(IdUtils.asFile("1.2.3"), fid);
	}
}
