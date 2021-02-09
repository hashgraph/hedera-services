package com.hedera.services.stats;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsSpeedometer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class MiscSpeedometersTest {
	double halfLife = 10.0;
	Platform platform;

	SpeedometerFactory factory;
	NodeLocalProperties properties;

	MiscSpeedometers subject;

	@BeforeEach
	public void setup() throws Exception {
		factory = mock(SpeedometerFactory.class);
		platform = mock(Platform.class);

		properties = mock(NodeLocalProperties.class);
		given(properties.statsRunningAvgHalfLifeSecs()).willReturn(halfLife);

		subject = new MiscSpeedometers(factory, properties);
	}

	@Test
	public void registersExpectedStatEntries() {
		// setup:
		StatEntry sync = mock(StatEntry.class);
		StatEntry async = mock(StatEntry.class);
		StatEntry retries = mock(StatEntry.class);
		StatEntry rejections = mock(StatEntry.class);

		given(factory.from(
				argThat(MiscSpeedometers.Names.SYNC_VERIFICATIONS::equals),
				argThat(MiscSpeedometers.Descriptions.SYNC_VERIFICATIONS::equals),
				any())).willReturn(sync);
		given(factory.from(
				argThat(MiscSpeedometers.Names.ASYNC_VERIFICATIONS::equals),
				argThat(MiscSpeedometers.Descriptions.ASYNC_VERIFICATIONS::equals),
				any())).willReturn(async);
		given(factory.from(
				argThat(MiscSpeedometers.Names.ACCOUNT_LOOKUP_RETRIES::equals),
				argThat(MiscSpeedometers.Descriptions.ACCOUNT_LOOKUP_RETRIES::equals),
				any())).willReturn(retries);
		given(factory.from(
				argThat(MiscSpeedometers.Names.PLATFORM_TXN_REJECTIONS::equals),
				argThat(MiscSpeedometers.Descriptions.PLATFORM_TXN_REJECTIONS::equals),
				any())).willReturn(rejections);

		// when:
		subject.registerWith(platform);

		// then:
		verify(platform).addAppStatEntry(retries);
		verify(platform).addAppStatEntry(sync);
		verify(platform).addAppStatEntry(async);
		verify(platform).addAppStatEntry(rejections);
	}

	@Test
	public void cyclesExpectedSpeedometers() {
		// setup:
		StatsSpeedometer retries = mock(StatsSpeedometer.class);
		StatsSpeedometer sync = mock(StatsSpeedometer.class);
		StatsSpeedometer async = mock(StatsSpeedometer.class);
		StatsSpeedometer rejections = mock(StatsSpeedometer.class);
		// and:
		subject.accountLookupRetries = retries;
		subject.syncVerifications = sync;
		subject.platformTxnRejections = rejections;
		subject.asyncVerifications = async;

		// when:
		subject.cycleAccountLookupRetries();
		subject.cycleAsyncVerifications();
		subject.cycleSyncVerifications();
		subject.cyclePlatformTxnRejections();

		// then:
		verify(retries).update(1.0);
		verify(rejections).update(1.0);
		verify(sync).update(1.0);
		verify(async).update(1.0);
	}
}
