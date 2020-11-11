package com.hedera.services.bdd.suites.misc;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class SleepOnly extends HapiApiSuite {
	private static final Logger log =
			LogManager.getLogger(com.hedera.services.bdd.suites.misc.SleepOnly.class);

	public static void main(String... args) {
		new SleepOnly().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				postiveTests()
		);
	}

	private List<HapiApiSpec> postiveTests() {
		return Arrays.asList(
				SleepOnly()
		);
	}

	private int sleepMin = 1;

	private HapiApiSpec SleepOnly() {

		return defaultHapiSpec("SleepOnly")
				.given(
						withOpContext((spec, opLog) -> {
							HapiPropertySource ciProps = spec.setup().ciPropertiesMap();
							if (ciProps.has("sleepMin")) {
								sleepMin = ciProps.getInteger("sleepMin");
								log.info("Client set sleepMin " + sleepMin);
							}
						})
				).when(
						sleepFor(sleepMin * 60 * 1000)
				).then(
				);
	}
}
