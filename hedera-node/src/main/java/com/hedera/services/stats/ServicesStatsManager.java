package com.hedera.services.stats;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.utils.Pause;
import com.swirlds.common.Platform;

import java.util.function.Function;

import static com.hedera.services.utils.SleepingPause.SLEEPING_PAUSE;

public class ServicesStatsManager {
	static Pause pause = SLEEPING_PAUSE;
	static Function<Runnable, Thread> loopFactory = loop -> new Thread(() -> {
		while (true) {
			loop.run();
		}
	});

	static final String SPEEDOMETER_UPDATE_THREAD_NAME_TPL = "SpeedometerUpdateThread%d";

	private final HapiOpCounters opCounters;
	private final MiscRunningAvgs runningAvgs;
	private final MiscSpeedometers speedometers;
	private final HapiOpSpeedometers opSpeedometers;
	private final NodeLocalProperties properties;

	public ServicesStatsManager(
			HapiOpCounters opCounters,
			MiscRunningAvgs runningAvgs,
			MiscSpeedometers speedometers,
			HapiOpSpeedometers opSpeedometers,
			NodeLocalProperties properties
	) {
		this.properties = properties;
		this.opCounters = opCounters;
		this.runningAvgs = runningAvgs;
		this.speedometers = speedometers;
		this.opSpeedometers = opSpeedometers;
	}

	public void initializeFor(Platform platform) {
		opCounters.registerWith(platform);
		runningAvgs.registerWith(platform);
		speedometers.registerWith(platform);
		opSpeedometers.registerWith(platform);

		platform.appStatInit();

		var updateThread = loopFactory.apply(() -> {
			pause.forMs(properties.statsHapiOpsSpeedometerUpdateIntervalMs());
			opSpeedometers.updateAll();
		});
		updateThread.setName(String.format(SPEEDOMETER_UPDATE_THREAD_NAME_TPL, platform.getSelfId().getId()));
		updateThread.start();
	}
}
