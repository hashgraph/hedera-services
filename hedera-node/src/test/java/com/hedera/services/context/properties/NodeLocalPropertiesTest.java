package com.hedera.services.context.properties;

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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.context.properties.Profile.DEV;
import static com.hedera.services.context.properties.Profile.PROD;
import static com.hedera.services.context.properties.Profile.TEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

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
		assertEquals(6L, subject.statsHapiOpsSpeedometerUpdateIntervalMs());
		assertEquals(7.0, subject.statsSpeedometerHalfLifeSecs());
		assertEquals(8.0, subject.statsRunningAvgHalfLifeSecs());
		assertEquals(logDir(9), subject.recordLogDir());
		assertEquals(10L, subject.recordLogPeriod());
		Assertions.assertTrue(subject.isRecordStreamEnabled());
		assertEquals(12, subject.recordStreamQueueCapacity());
		assertEquals(13, subject.queryBlobLookupRetries());
		assertEquals(14L, subject.nettyProdKeepAliveTime());
		assertEquals("hedera1.crt", subject.nettyTlsCrtPath());
		assertEquals("hedera2.key", subject.nettyTlsKeyPath());
		assertEquals(15L, subject.nettyProdKeepAliveTimeout());
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
		assertEquals(7L, subject.statsHapiOpsSpeedometerUpdateIntervalMs());
		assertEquals(8.0, subject.statsSpeedometerHalfLifeSecs());
		assertEquals(9.0, subject.statsRunningAvgHalfLifeSecs());
		assertEquals(logDir(10), subject.recordLogDir());
		assertEquals(11L, subject.recordLogPeriod());
		Assertions.assertFalse(subject.isRecordStreamEnabled());
		assertEquals(13, subject.recordStreamQueueCapacity());
		assertEquals(14, subject.queryBlobLookupRetries());
		assertEquals(15L, subject.nettyProdKeepAliveTime());
		assertEquals("hedera2.crt", subject.nettyTlsCrtPath());
		assertEquals("hedera3.key", subject.nettyTlsKeyPath());
		assertEquals(16L, subject.nettyProdKeepAliveTimeout());
	}

	private void givenPropsWithSeed(int i) {
		given(properties.getIntProperty("grpc.port")).willReturn(i);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(i + 1);
		given(properties.getIntProperty("precheck.account.maxLookupRetries")).willReturn(i + 2);
		given(properties.getIntProperty("precheck.account.lookupRetryBackoffIncrementMs")).willReturn(i + 3);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(LEGACY_ENV_ORDER[(i + 4) % 3]);
		given(properties.getLongProperty("stats.hapiOps.speedometerUpdateIntervalMs")).willReturn(i + 5L);
		given(properties.getDoubleProperty("stats.speedometerHalfLifeSecs")).willReturn(i + 6.0);
		given(properties.getDoubleProperty("stats.runningAvgHalfLifeSecs")).willReturn(i + 7.0);
		given(properties.getStringProperty("hedera.recordStream.logDir")).willReturn(logDir(i + 8));
		given(properties.getLongProperty("hedera.recordStream.logPeriod")).willReturn(i + 9L);
		given(properties.getBooleanProperty("hedera.recordStream.isEnabled")).willReturn(i % 2 == 1);
		given(properties.getIntProperty("hedera.recordStream.queueCapacity")).willReturn(i + 11);
		given(properties.getIntProperty("queries.blob.lookupRetries")).willReturn(i + 12);
		given(properties.getLongProperty("netty.prod.keepAliveTime")).willReturn(i + 13L);
		given(properties.getStringProperty("netty.tlsCrt.path")).willReturn("hedera" + i + ".crt");
		given(properties.getStringProperty("netty.tlsKey.path")).willReturn("hedera" + (i + 1) + ".key");
		given(properties.getLongProperty("netty.prod.keepAliveTimeout")).willReturn(i + 14L);
	}

	static String logDir(int num) {
		return "myRecords/dir" + num;
	}
}
