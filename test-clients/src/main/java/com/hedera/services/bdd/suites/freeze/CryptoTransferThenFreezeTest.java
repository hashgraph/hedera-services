package com.hedera.services.bdd.suites.freeze;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.perf.CryptoTransferLoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class CryptoTransferThenFreezeTest extends CryptoTransferLoadTest {
	private static final Logger log = LogManager.getLogger(CryptoTransferThenFreezeTest.class);

	public static void main(String... args) {
		parseArgs(args);

		CryptoTransferThenFreezeTest suite = new CryptoTransferThenFreezeTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runCryptoTransfers(), freezeAfterTransfers());
	}

	private HapiApiSpec freezeAfterTransfers() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		return defaultHapiSpec("FreezeAfterTransfers")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString()))
				.when(freeze().startingIn(1).minutes().andLasting(2).minutes().payingWith(GENESIS)).then(
						// sleep for a while to wait for this freeze transaction be handled
						UtilVerbs.sleepFor(75_000)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
