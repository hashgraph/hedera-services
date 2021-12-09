package com.hedera.services.bdd.suites.crypto;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalanceWithAlias;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfoWithAlias;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdateWithAlias;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enablingAutoRenewWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

public class AutoAccountUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AutoAccountUpdateSuite.class);
	private static final long initialBalance = 1000L;

	public static void main(String... args) {
		new AutoAccountUpdateSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						updateKeyOnAutoCreatedAccount(),
						accountCreatedAfterAliasAccountExpires(),
//						accountCreatedAfterAliasAccountExpiresAndDelete()
				}
		);
	}

	private HapiApiSpec accountCreatedAfterAliasAccountExpires() {
		final var briefAutoRenew = 3L;
		return defaultHapiSpec("AccountCreatedAfterAliasAccountExpires")
				.given(
						newKeyNamed("alias"),
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(enablingAutoRenewWith(briefAutoRenew, 20 * briefAutoRenew)),
						cryptoCreate("randomPayer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfoWithAlias("alias").has(accountWith()
								.autoRenew(THREE_MONTHS_IN_SECONDS)
								.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 1, 10))
				).then(
						cryptoUpdateWithAlias("alias").autoRenewPeriod(briefAutoRenew).signedBy(
								"alias", "randomPayer", DEFAULT_PAYER),
						sleepFor(2 * briefAutoRenew * 1_000L + 500L),
						getAccountBalanceWithAlias("alias"),

						// Need to know why its INVALID_ACCOUNT_ID, same reason as Delete
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasChildRecordCount(0),
						getAccountInfoWithAlias("alias").has(
								accountWith()
										.expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 1, 10)),

						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

	private HapiApiSpec updateKeyOnAutoCreatedAccount() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));

		return defaultHapiSpec("updateKeyOnAutoCreatedAccount")
				.given(
						newKeyNamed("alias"),
						newKeyNamed("complexKey").shape(ENOUGH_UNIQUE_SIGS),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(
								tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfoWithAlias("alias").has(accountWith()
								.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1)
								.alias("alias"))
				).then(
						cryptoUpdateWithAlias("alias")
								.key("complexKey")
								.payingWith("payer")
								.signedBy("alias", "complexKey", "payer", DEFAULT_PAYER),
						getAccountInfoWithAlias("alias").has(
								accountWith()
										.expectedBalanceWithChargedUsd((ONE_HUNDRED_HBARS), 0.05, 0.1)
										.key("complexKey")));
	}

	// Can't be done without property change. Need review
	private HapiApiSpec accountCreatedAfterAliasAccountExpiresAndDelete() {
		final var briefAutoRenew = 3L;
		return defaultHapiSpec("AccountCreatedAfterAliasAccountExpires")
				.given(
						newKeyNamed("alias"),
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(enablingAutoRenewWith(briefAutoRenew, 2 * briefAutoRenew)),
						cryptoCreate("randomPayer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfoWithAlias("alias").has(accountWith().autoRenew(THREE_MONTHS_IN_SECONDS))
				).then(
						cryptoUpdateWithAlias("alias").autoRenewPeriod(briefAutoRenew).signedBy(
								"alias", "randomPayer"),
						sleepFor(2 * briefAutoRenew * 1_000L + 500L),
						getAccountBalance("alias").hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID),

						// Need to know why its INVALID_ACCOUNT_ID, same reason as Delete
						cryptoTransfer(tinyBarsFromToWithAlias("randomPayer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasChildRecordCount(1),

						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

}
