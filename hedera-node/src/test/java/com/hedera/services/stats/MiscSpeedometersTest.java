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

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;


@ExtendWith(MockitoExtension.class)
class MiscSpeedometersTest {
	private static final double halfLife = 10.0;

	@Mock
	private Platform platform;
	@Mock
	private SpeedometerMetric syncVerifies;
	@Mock
	private SpeedometerMetric txnRejections;

	private MiscSpeedometers subject;

	@BeforeEach
	void setup() {
		platform = mock(Platform.class);

		subject = new MiscSpeedometers(halfLife);
	}

	@Test
	void registersExpectedStatEntries() {
		subject.setSyncVerifications(syncVerifies);
		subject.setPlatformTxnRejections(txnRejections);

		subject.registerWith(platform);

		verify(platform).addAppMetrics(syncVerifies, txnRejections);
	}

	@Test
	void cyclesExpectedSpeedometers() {
		subject.cycleSyncVerifications();
		subject.cyclePlatformTxnRejections();

		assertNotEquals(0.0, subject.getPlatformTxnRejections().getStatsBuffered().getMean());
		assertNotEquals(0.0, subject.getSyncVerifications().getStatsBuffered().getMean());
	}
}
