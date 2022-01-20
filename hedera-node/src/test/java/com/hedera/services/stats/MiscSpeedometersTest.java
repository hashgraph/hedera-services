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

import com.swirlds.common.Platform;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsSpeedometer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class MiscSpeedometersTest {
	private static final double halfLife = 10.0;

	private Platform platform;
	private SpeedometerFactory factory;

	private MiscSpeedometers subject;

	@BeforeEach
	void setup() throws Exception {
		factory = mock(SpeedometerFactory.class);
		platform = mock(Platform.class);

		subject = new MiscSpeedometers(factory, halfLife);
	}

	@Test
	void registersExpectedStatEntries() {
		final var sync = mock(StatEntry.class);
		final var async = mock(StatEntry.class);
		final var retries = mock(StatEntry.class);
		final var rejections = mock(StatEntry.class);
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

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(retries);
		verify(platform).addAppStatEntry(sync);
		verify(platform).addAppStatEntry(async);
		verify(platform).addAppStatEntry(rejections);
	}

	@Test
	void cyclesExpectedSpeedometers() {
		final var retries = mock(StatsSpeedometer.class);
		final var sync = mock(StatsSpeedometer.class);
		final var async = mock(StatsSpeedometer.class);
		final var rejections = mock(StatsSpeedometer.class);
		subject.accountLookupRetries = retries;
		subject.syncVerifications = sync;
		subject.platformTxnRejections = rejections;
		subject.asyncVerifications = async;

		subject.cycleAccountLookupRetries();
		subject.cycleAsyncVerifications();
		subject.cycleSyncVerifications();
		subject.cyclePlatformTxnRejections();

		verify(retries).update(1.0);
		verify(rejections).update(1.0);
		verify(sync).update(1.0);
		verify(async).update(1.0);
	}
}
