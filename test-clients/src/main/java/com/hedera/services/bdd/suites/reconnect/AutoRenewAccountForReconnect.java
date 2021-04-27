package com.hedera.services.bdd.suites.reconnect;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

public class AutoRenewAccountForReconnect extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AutoRenewAccountForReconnect.class);

	public static void main(String... args) {
		new AutoRenewAccountForReconnect().runSuiteSync();
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
						// TODO sleep while the last node disconnects and reconnects
						cryptoTransfer(tinyBarsFromTo(GENESIS, NODE, 1L)).via("triggeringTransaction")
						// Do above cryptoTransfer while the node is still disconnected
						// TODO sleep finishes and Node reconnects again here
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

