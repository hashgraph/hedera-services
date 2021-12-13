package com.hedera.services.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TestClassWithLogger {
	private static final Logger log = LogManager.getLogger(TestClassWithLogger.class);

	public void testLogger(String arg) {
		log.warn("We’re doomed. " + arg);
	}
}
