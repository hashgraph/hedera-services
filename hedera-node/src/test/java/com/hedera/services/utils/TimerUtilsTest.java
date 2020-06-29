package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;
import org.junit.jupiter.api.Test;
//import org.junit.runner.RunWith;
//import org.mockito.Mockito;
//import org.powermock.core.classloader.annotations.PrepareForTest;
//import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Timer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

//@RunWith(PowerMockRunner.class)
//@PrepareForTest(TimerUtils.class)
class TimerUtilsTest {

	@Test
	void testInitStatsDumpTimers() throws Exception	{
		HederaNodeStats mockStats = mock(HederaNodeStats.class);

		TimerUtils.initStatsDumpTimers(mockStats);
	}
}