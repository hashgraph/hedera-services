package com.hedera.services.utils;

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
