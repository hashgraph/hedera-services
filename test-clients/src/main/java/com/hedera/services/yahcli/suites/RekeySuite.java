package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.yahcli.suites.Utils.extractAccount;

public class RekeySuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RekeySuite.class);

	private final String account;
	private final String replKeyLoc;
	private final Map<String, String> specConfig;

	public RekeySuite(
			Map<String, String> specConfig,
			String account,
			String replKeyLoc
	) {
		this.specConfig = specConfig;
		this.replKeyLoc = replKeyLoc;
		this.account = extractAccount(account);
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(rekey());
	}

	private HapiApiSpec rekey() {
		final var replKey = "replKey";

		return HapiApiSpec.customHapiSpec("rekey" + account)
				.withProperties(specConfig)
				.given(
				).when( ).then(
						cryptoUpdate(account)
								.signedBy(DEFAULT_PAYER, replKey)
								.key(replKey)
								.noLogging()
								.yahcliLogging()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
