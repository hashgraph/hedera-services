package com.hedera.services.bdd.suites.schedule;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.perf.NeehaMixedOpsSetup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class ScheduleRestartSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NeehaMixedOpsSetup.class);

	public static void main(String... args) {
		new ScheduleRestartSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						scheduleTransactionsScenario()
				}
		);
	}

	private HapiApiSpec scheduleTransactionsScenario() {
		return HapiApiSpec.defaultHapiSpec("scheduleTransactionsScenario").given().when().then(
				withOpContext((spec, opLog) -> runBasedOnCIProps(spec)
				));
	}

	private HapiApiSpec runBasedOnCIProps(HapiApiSpec spec) {
		return spec.setup().ciPropertiesMap().getBoolean("post") ?
				transactionsAfterRestart() : transactionsBeforeRestart();
	}

	private HapiApiSpec transactionsAfterRestart() {
		return HapiApiSpec.defaultHapiSpec("Freeze").given().when().then(
				freeze().payingWith(GENESIS)
						.startingIn(60).seconds()
						.andLasting(1).minutes()
		);
	}

	private HapiApiSpec transactionsBeforeRestart() {
		long ONE_YEAR_IN_SECS = 365 * 24 * 60 * 60;
		int numScheduledTxns = 10;
		return HapiApiSpec.defaultHapiSpec("transactionsBeforeRestart")
				.given(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of(
										"ledger.schedule.txExpiryTimeSecs", "" + ONE_YEAR_IN_SECS
								)),
						cryptoCreate("sender")
								.advertisingCreation()
								.balance(ONE_HBAR),
						cryptoCreate("receiver")
								.key(GENESIS)
								.advertisingCreation()
								.balance(0L).receiverSigRequired(true),
						cryptoCreate("tokenTreasury")
								.key(GENESIS)
								.advertisingCreation()
								.balance(ONE_HBAR),
						tokenCreate("wellKnown")
								.advertisingCreation()
								.initialSupply(Long.MAX_VALUE),
						cryptoCreate("tokenReceiver")
								.advertisingCreation(),
						tokenAssociate("tokenReceiver", "wellKnown"),
						createTopic("wellKnownTopic")
								.advertisingCreation()
				).when(
						inParallel(IntStream.range(0, numScheduledTxns).mapToObj(i ->
								scheduleCreate("schedule" + i,
										cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1))
												.signedBy("sender")
								)
										.advertisingCreation()
										.fee(A_HUNDRED_HBARS)
										.signedBy(DEFAULT_PAYER)
										.inheritingScheduledSigs()
										.withEntityMemo("This is the " + i + "th scheduled txn.")
						).toArray(HapiSpecOperation[]::new))
				).then(
						freeze().payingWith(GENESIS)
								.startingIn(60).seconds()
								.andLasting(1).minutes()
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
