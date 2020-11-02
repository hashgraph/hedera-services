package com.hedera.services.bdd.suites.freeze;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.Arrays;
import java.util.List;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;

public class SimpleFreezeOnly extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeSuite.class);

	public static void main(String... args) {
		new FreezeSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				SimpleFreezeOnly()
		);
	}

	private HapiApiSpec SimpleFreezeOnly() {
		return defaultHapiSpec("SimpleFreezeOnly")
				.given(
				).when(
						freeze().payingWith(GENESIS).startingIn(60).seconds().andLasting(10).minutes()
				).then(
				);
	}
}
