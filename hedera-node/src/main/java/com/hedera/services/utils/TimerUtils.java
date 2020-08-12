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

import java.util.Timer;

public class TimerUtils {
	private static StatsDumpTimerTask dumpHederaNodeStatsTask;
	private static Timer statsDumpTimer;

	final private static int INITIAL_DELAY_DUMP_STATS = 30; // in seconds

	public static void initStatsDumpTimers(HederaNodeStats stats) {
		dumpHederaNodeStatsTask = new StatsDumpTimerTask(stats);
		statsDumpTimer = new Timer(true);
	}

	public static void startStatsDumpTimer(int timerValueInSeconds) {
		statsDumpTimer.scheduleAtFixedRate(
				dumpHederaNodeStatsTask,
				INITIAL_DELAY_DUMP_STATS * 1000,
				timerValueInSeconds * 1000);
	}

	public static void stopStatsDumpTimer() {
		statsDumpTimer.cancel();
	}
}
