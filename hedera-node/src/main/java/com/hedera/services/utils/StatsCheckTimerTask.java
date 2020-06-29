package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;

import java.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class StatsCheckTimerTask extends TimerTask {
	public static Logger log = LogManager.getLogger(StatsCheckTimerTask.class);

	private HederaNodeStats stats;

	@Override
	public void run() {
		log.warn("Now dump HederaNodeStats...");
		stats.dumpHederaNodeStats();
	}

	public StatsCheckTimerTask(HederaNodeStats stats) {
		this.stats = stats;
	}

}
