package com.hedera.services.utils;

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

import com.hedera.services.legacy.services.stats.HederaNodeStats;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class TimerUtilsTest {

	@Test
	public void testInitStatsDumpTimers() {
		// Given:
		HederaNodeStats mockStats = mock(HederaNodeStats.class);

		// Then:
		TimerUtils.initStatsDumpTimers(mockStats);
	}

	@Test
	public void testStartStatsDumpTimer() {
		// Given:
		HederaNodeStats mockStats = mock(HederaNodeStats.class);
		TimerUtils.initStatsDumpTimers(mockStats);

		// Then:
		TimerUtils.startStatsDumpTimer(10);
	}

	@Test
	public void testStopStatsDumpTimer() {
		// Given:
		HederaNodeStats mockStats = mock(HederaNodeStats.class);
		TimerUtils.initStatsDumpTimers(mockStats);

		// Then:
		TimerUtils.stopStatsDumpTimer();
	}


}
