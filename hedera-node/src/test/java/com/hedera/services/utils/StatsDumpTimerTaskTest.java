package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnitPlatform.class)
class StatsDumpTimerTaskTest {

	@Test
	void testRun() {
		// Given:
		HederaNodeStats mockStats = mock(HederaNodeStats.class);
		when(mockStats.dumpHederaNodeStats()).thenReturn("");

		// When:
		StatsDumpTimerTask timerTask = new StatsDumpTimerTask(mockStats);
		StatsDumpTimerTask mock = spy(timerTask);
		mock.run();
		// Then:
		verify(mock, times(1)).run();
	}
}