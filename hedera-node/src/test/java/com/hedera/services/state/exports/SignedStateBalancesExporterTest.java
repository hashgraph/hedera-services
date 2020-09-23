package com.hedera.services.state.exports;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Instant;

@RunWith(JUnitPlatform.class)
class SignedStateBalancesExporterTest {
	GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	long ledgerFloat = Long.MAX_VALUE;
	Instant now = Instant.now();
	Instant shortlyAfter = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() / 2);
	Instant anEternityLater = now.plusSeconds(dynamicProperties.balancesExportPeriodSecs() * 2);

	PropertySource properties;

	SignedStateBalancesExporter subject;

	@BeforeEach
	public void setUp() throws Exception {
		properties = mock(PropertySource.class);
		given(properties.getLongProperty("ledger.totalTinyBarFloat")).willReturn(ledgerFloat);

		subject = new SignedStateBalancesExporter(properties, dynamicProperties);
	}

	@Test
	public void initsAsExpected() {
		// expect:
		assertEquals(ledgerFloat, subject.expectedFloat);
		assertEquals(SignedStateBalancesExporter.NEVER, subject.periodEnd);
	}

	@Test
	public void exportsWhenPeriodSecsHaveElapsed() {
		assertFalse(subject.isTimeToExport(now));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertFalse(subject.isTimeToExport(shortlyAfter));
		assertEquals(now.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
		assertTrue(subject.isTimeToExport(anEternityLater));
		assertEquals(anEternityLater.plusSeconds(dynamicProperties.balancesExportPeriodSecs()), subject.periodEnd);
	}
}