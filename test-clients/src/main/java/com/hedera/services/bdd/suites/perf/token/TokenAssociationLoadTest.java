package com.hedera.services.bdd.suites.perf.token;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenAssociationLoadTest  extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenAssociationLoadTest.class);

	private AtomicInteger maxOpsPerSec = new AtomicInteger(500);
	@Override
	protected Logger getResultsLogger() {
		return null;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return null;
	}
}
