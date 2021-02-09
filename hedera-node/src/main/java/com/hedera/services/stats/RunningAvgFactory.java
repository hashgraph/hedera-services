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

public interface RunningAvgFactory {
	default StatEntry from(String name, String desc, StatsRunningAverage runningAvg) {
		return new StatEntry(
				"app",
				name,
				desc,
				"%,13.6f",
				runningAvg,
				newHalfLife -> {
					runningAvg.reset(newHalfLife);
					return runningAvg;
				},
				runningAvg::reset,
				runningAvg::getWeightedMean);
	}
}
