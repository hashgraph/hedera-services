package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiFreeze;

public class FreezeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeSuite.class);

	private final Instant freezeStarttime;

	private final Map<String, String> specConfig;

	public FreezeSuite(final Map<String, String> specConfig, final Instant freezeStarttime) {
		this.specConfig = specConfig;
		this.freezeStarttime = freezeStarttime;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		List<HapiApiSpec> specToRun = new ArrayList<>();
		specToRun.add(freezeSystem(freezeStarttime));
		return specToRun;
	}

	private HapiApiSpec freezeSystem(Instant freezeStarttime) {
		return HapiApiSpec.customHapiSpec(("freezeSystem"))
				.withProperties(specConfig)
				.given().when()
				.then(
						//getAccountBalance(accountID),
						hapiFreeze(freezeStarttime)
								.noLogging()
								.yahcliLogging()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}