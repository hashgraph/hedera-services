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

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.times;
import static org.mockito.BDDMockito.verify;

class RunningAvgFactoryTest {
	RunningAvgFactory subject = new RunningAvgFactory() { };

	@Test
	void constructsExpectedEntry() {
		// setup:
		var name = "MyOp";
		var desc = "Happy thoughts";
		double halfLife = 1.23;
		double something = 3.21;
		StatsRunningAverage runningAvg = mock(StatsRunningAverage.class);

		given(runningAvg.getWeightedMean()).willReturn(something);

		// when:
		StatEntry entry = subject.from(name, desc, runningAvg);
		entry.init();
		entry.reset();

		// then:
		assertEquals("app", entry.getCategory());
		assertEquals(name, entry.getName());
		assertEquals(desc, entry.getDescription());
		assertEquals("%,13.6f", entry.getFormat());
		assertSame(runningAvg, entry.getStatsBuffered());
		verify(runningAvg, times(2)).reset(halfLife);
	}
}
