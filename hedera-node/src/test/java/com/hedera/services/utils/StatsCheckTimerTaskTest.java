package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnitPlatform.class)
class StatsCheckTimerTaskTest {

	@Test
	void testRun() {
		// Given:
		HederaNodeStats mockStats = mock(HederaNodeStats.class);
		when(mockStats.dumpHederaNodeStats()).thenReturn("");

		// When:
		StatsCheckTimerTask timerTask = new StatsCheckTimerTask(mockStats);
		StatsCheckTimerTask mock = spy(timerTask);
		mock.run();
		// Then:
		verify(mock, times(1)).run();
	}
}