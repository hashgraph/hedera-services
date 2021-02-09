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

import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.*;

class RunningAvgFactoryTest {
	RunningAvgFactory subject = new RunningAvgFactory() { };

	@Test
	public void constructsExpectedEntry() {
		// setup:
		var name = "MyOp";
		var desc = "Happy thoughts";
		double halfLife = 1.23;
		double something = 3.21;
		StatsRunningAverage runningAvg = mock(StatsRunningAverage.class);

		given(runningAvg.getWeightedMean()).willReturn(something);

		// when:
		StatEntry entry = subject.from(name, desc, runningAvg);
		// and:
		var resetRunningAvg = entry.init.apply(halfLife);
		// and:
		entry.reset.accept(halfLife);

		// then:
		assertEquals("app", entry.category);
		assertEquals(name, entry.name);
		assertEquals(desc, entry.desc);
		assertEquals("%,13.6f", entry.format);
		assertSame(runningAvg, entry.buffered);
		// and:
		assertSame(runningAvg, resetRunningAvg);
		verify(runningAvg, times(2)).reset(halfLife);
		// and:
		assertEquals(something, entry.supplier.get());
	}
}
