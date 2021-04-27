package com.hedera.services.bdd.suites.reconnect;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withLiveNode;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

public class AutoRenewEntitiesForReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AutoRenewEntitiesForReconnect.class);

	public static void main(String... args) {
		new AutoRenewEntitiesForReconnect().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				autoRenewAccountGetsDeletedOnReconnectingNodeAsWell()
		);
	}

	private HapiApiSpec autoRenewAccountGetsDeletedOnReconnectingNodeAsWell() {
		String autoDeleteAccount = "autoDeleteAccount";
		int autoRenewSecs = 10;
		return defaultHapiSpec("AutoRenewAccountGetsDeletedOnReconnectingNodeAsWell")
				.given(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(Map.of(
										"ledger.autoRenewPeriod.minDuration", String.valueOf(autoRenewSecs),
										"autorenew.gracePeriod", "0",
										"autorenew.numberOfEntitiesToScan", "100",
										"autorenew.maxNumberOfEntitiesToRenewOrDelete", "2"))
								.erasingProps(Set.of("minimumAutoRenewDuration")),
						cryptoCreate(autoDeleteAccount).autoRenewSecs(autoRenewSecs).balance(0L)
				)
				.when(
						sleepFor(15 * 1000),
						withLiveNode("0.0.8")
								.within(60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10),
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggeringTransaction"),
						sleepFor(30 * 1000),
						withLiveNode("0.0.8")
								.within(60, TimeUnit.SECONDS)
								.loggingAvailabilityEvery(30)
								.sleepingBetweenRetriesFor(10)
				)
				.then(
						getAccountBalance(autoDeleteAccount)
								.setNode("0.0.8")
								.hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

