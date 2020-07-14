package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class TimerUtilsTest {

	@Test
	public void testInitStatsDumpTimers() 	{
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