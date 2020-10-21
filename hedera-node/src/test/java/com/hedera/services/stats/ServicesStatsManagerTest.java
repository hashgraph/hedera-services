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
import com.hedera.services.utils.SleepingPause;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class ServicesStatsManagerTest {
	long updateIntervalMs = 1_234;

	Pause pause;
	Function<Runnable, Thread> threads;
	Platform platform;

	HapiOpCounters counters;
	HapiOpSpeedometers speedometers;
	NodeLocalProperties properties;

	ServicesStatsManager subject;

	@BeforeEach
	public void setup() throws Exception {
		pause = mock(Pause.class);
		threads = mock(Function.class);

		ServicesStatsManager.eternalRunFactory = threads;
		ServicesStatsManager.pause = pause;

		platform = mock(Platform.class);
		given(platform.getSelfId()).willReturn(new NodeId(false, 123L));
		counters = mock(HapiOpCounters.class);
		speedometers = mock(HapiOpSpeedometers.class);
		properties = mock(NodeLocalProperties.class);
		given(properties.statsHapiSpeedometerUpdateIntervalMs()).willReturn(updateIntervalMs);

		subject = new ServicesStatsManager(counters, speedometers, properties);
	}


	@AfterEach
	public void cleanup() throws Exception {
		ServicesStatsManager.pause = SleepingPause.SLEEPING_PAUSE;
		ServicesStatsManager.eternalRunFactory = runnable -> new Thread(() -> {
			while (true) {
				runnable.run();
			}
		});
	}

	@Test
	public void initsAsExpected() {
		// setup:
		Thread thread = mock(Thread.class);
		ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);

		given(pause.forMs(anyLong())).willReturn(true);
		given(threads.apply(captor.capture())).willReturn(thread);

		// when:
		subject.initializeFor(platform);

		// then:
		verify(counters).registerWith(platform);
		verify(speedometers).registerWith(platform);
		verify(thread).start();
		verify(thread).setName("StatsUpdateNode123");
		// and when:
		captor.getValue().run();
		// then:
		verify(pause).forMs(updateIntervalMs);
		verify(speedometers).updateAll();
	}
}