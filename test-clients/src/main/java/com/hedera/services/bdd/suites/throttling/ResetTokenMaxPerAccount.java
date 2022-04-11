package com.hedera.services.bdd.suites.throttling;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class ResetTokenMaxPerAccount extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(
			ResetTokenMaxPerAccount.class);

	public static void main(String... args) {
		new ResetTokenMaxPerAccount().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				resetTokenMaxPerAccount()
		);
	}

	protected HapiApiSpec resetTokenMaxPerAccount() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		return defaultHapiSpec("RunCryptoTransfersWithAutoAccounts")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(
										Map.of("tokens.maxPerAccount", "10000"))
				).then(
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


