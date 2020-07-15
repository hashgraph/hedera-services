package com.hedera.services.utils;

import com.hedera.services.legacy.services.stats.HederaNodeStats;

import java.util.TimerTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class StatsDumpTimerTask extends TimerTask {
	public static Logger log = LogManager.getLogger(StatsDumpTimerTask.class);

	private HederaNodeStats stats;

	@Override
	public void run() {
		log.info("Dumping stats...");
		stats.dumpHederaNodeStats();
	}

	public StatsDumpTimerTask(HederaNodeStats stats) {
		this.stats = stats;
	}

}
