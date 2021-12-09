package com.hedera.services.bdd.suites.crypto;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccountString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.disablingAutoRenewWithDefaults;
import static com.hedera.services.bdd.suites.autorenew.AutoRenewConfigChoices.enablingAutoRenewWith;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.randomValidEd25519Alias;
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
						accountCreatedAfterAliasAccountExpires()
				}
		);
	}

	private HapiApiSpec updateKeyOnAutoCreatedAccount() {
		SigControl ENOUGH_UNIQUE_SIGS = KeyShape.threshSigs(2,
				KeyShape.threshSigs(1, OFF, OFF, OFF, OFF, OFF, OFF, ON),
				KeyShape.threshSigs(3, ON, ON, ON, OFF, OFF, OFF, OFF));

		final var alias = randomValidEd25519Alias();
		return defaultHapiSpec("updateKeyOnAutoCreatedAccount")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)).via("transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo(alias).has(accountWith()
								.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1)
								.alias(alias))
				).then(
						withOpContext((spec, opLog) -> {
							newKeyNamed("key").shape(ENOUGH_UNIQUE_SIGS);
							final var aliasAccount = spec.registry().getAccountID(alias.toStringUtf8());
							final var account = asAccountString(aliasAccount);

							cryptoUpdate(account)
									.key("key")
									.payingWith("payer");
							getAccountInfo(account).has(
									accountWith()
											.expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.1)
											.key("key"));
						}));
	}


	private HapiApiSpec accountCreatedAfterAliasAccountExpires() {
		final var briefAutoRenew = 3L;
		final var alias = randomValidEd25519Alias();
		return defaultHapiSpec("AccountCreatedAfterAliasAccountExpires")
				.given(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(enablingAutoRenewWith(briefAutoRenew, 2 * briefAutoRenew)),
						cryptoCreate("randomPayer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("randomPayer", alias, ONE_HUNDRED_HBARS)).via("transferTxn"),
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo(alias).has(accountWith().autoRenew(THREE_MONTHS_IN_SECONDS))
				).then(
						withOpContext((spec, opLog) -> {
							final var detachedAccount = spec.registry().getAccountID(alias.toStringUtf8());
							final var account = asAccountString(detachedAccount);
							var op = cryptoUpdate(alias.toStringUtf8()).autoRenewPeriod(briefAutoRenew).signedBy(
									alias.toStringUtf8(), "randomPayer").logged();
							var op2 = sleepFor(2 * briefAutoRenew * 1_000L + 500L);
							var op3 = getAccountBalance(alias).hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID);
							CustomSpecAssert.allRunFor(spec, op, op2, op3);
						}),
						cryptoTransfer(tinyBarsFromTo("randomPayer", alias, ONE_HUNDRED_HBARS)).via("transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasChildRecordCount(1),
						getAccountInfo(alias).has(accountWith().autoRenew(THREE_MONTHS_IN_SECONDS)),


						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(disablingAutoRenewWithDefaults())
				);
	}

}
