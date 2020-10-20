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

import static com.hedera.services.context.properties.Profile.DEV;
import static com.hedera.services.context.properties.Profile.PROD;
import static com.hedera.services.context.properties.Profile.TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class NodeLocalPropertiesTest {
	PropertySource properties;

	NodeLocalProperties subject;

	private static final Profile[] LEGACY_ENV_ORDER = { DEV, PROD, TEST };

	@BeforeEach
	public void setup() {
		properties = mock(PropertySource.class);
	}

	@Test
	public void constructsAsExpected() {
		givenPropsWithSeed(1);

		// when:
		subject = new NodeLocalProperties(properties);

		// expect:
		assertEquals(1, subject.port());
		assertEquals(2, subject.tlsPort());
		assertEquals(3, subject.precheckLookupRetries());
		assertEquals(4, subject.precheckLookupRetryBackoffMs());
		assertEquals(TEST, subject.activeProfile());
		assertEquals(6L, subject.statsHapiSpeedometerUpdateIntervalMs());
		assertEquals(7.0, subject.statsHapiSpeedometerHalfLifeSecs());
	}

	@Test
	public void reloadWorksAsExpected() {
		givenPropsWithSeed(2);

		// when:
		subject = new NodeLocalProperties(properties);

		// expect:
		assertEquals(2, subject.port());
		assertEquals(3, subject.tlsPort());
		assertEquals(4, subject.precheckLookupRetries());
		assertEquals(5, subject.precheckLookupRetryBackoffMs());
		assertEquals(DEV, subject.activeProfile());
		assertEquals(7L, subject.statsHapiSpeedometerUpdateIntervalMs());
		assertEquals(8.0, subject.statsHapiSpeedometerHalfLifeSecs());
	}

	private void givenPropsWithSeed(int i) {
		given(properties.getIntProperty("grpc.port")).willReturn(i);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(i + 1);
		given(properties.getIntProperty("precheck.account.maxLookupRetries")).willReturn(i + 2);
		given(properties.getIntProperty("precheck.account.lookupRetryBackoffIncrementMs")).willReturn(i + 3);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(LEGACY_ENV_ORDER[(i + 4) % 3]);
		given(properties.getLongProperty("stats.hapi.speedometerUpdateIntervalMs")).willReturn(i + 5L);
		given(properties.getDoubleProperty("stats.hapi.speedometerHalfLifeSecs")).willReturn(i + 6.0);
	}
}
