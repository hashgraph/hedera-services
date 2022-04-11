package com.hedera.services.bdd.suites.throttling;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;

public class ResetThrottleSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(
			com.hedera.services.bdd.suites.throttling.ResetThrottleSuite.class);

	public static void main(String... args) {
		new ResetThrottleSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				resetThrottle()
		);
	}

	protected HapiApiSpec resetThrottle() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		var defaultThrottles = protoDefsFromResource("testSystemFiles/throttles-dev.json");
		return defaultHapiSpec("RunCryptoTransfersWithAutoAccounts")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						fileUpdate(THROTTLE_DEFS)
								.payingWith(GENESIS)
								.contents(defaultThrottles.toByteArray())
				).then(
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


