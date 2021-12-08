package com.hedera.services.bdd.suites.crypto;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.PropertySource.asAccountString;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.asKey;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.randomValidEd25519Alias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class AutoAccountCreationSuite extends HapiApiSuite {
	private static final long initialBalance = 1000L;
	private static final ByteString aliasContent = ByteString.copyFromUtf8(
			"a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771");
	private static final Key validEd25519Key = Key.newBuilder().setEd25519(aliasContent).build();
	private static final ByteString valid25519Alias = validEd25519Key.toByteString();
	public static final String AUTO_MEMO = "auto-created account";

	private static final Logger log = LogManager.getLogger(AutoAccountCreationSuite.class);

	public static void main(String... args) {
		new AutoAccountCreationSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						autoAccountCreationsHappyPath(),
//						autoAccountCreationBadAlias(),
//						autoAccountCreationUnsupportedAlias(),
//						transferToAccountAutoCreatedUsingAlias(),
//						transferToAccountAutoCreatedUsingAccount(),
						autoAccountCreationWorksWhenUsingAliasOfDeletedAccount(),
//						transferFromAliasToAlias(),
//						multipleAutoAccountCreations(),
//						accountCreatedIfAliasUsedAsPubKey(),
				}
		);
	}

	private HapiApiSpec accountCreatedIfAliasUsedAsPubKey() {
		final var alias = randomValidEd25519Alias();
		Key aliasKey = asKey(alias);
		return defaultHapiSpec("AccountCreatedIfAliasUsedAsPubKey")
				.given(
						cryptoCreate("payer1").balance(initialBalance * ONE_HBAR).key(aliasKey)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer1", alias, ONE_HUNDRED_HBARS)).via("transferTxn")
				).then(
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer1").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)
										.noAlias()
						),
						getAccountInfo(alias).has(
										accountWith()
												.key(aliasKey)
												.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1)
												.alias(alias)
												.autoRenew(THREE_MONTHS_IN_SECONDS)
												.receiverSigReq(false))
								.logged()
				);
	}

	private HapiApiSpec autoAccountCreationWorksWhenUsingAliasOfDeletedAccount() {
		return defaultHapiSpec("AutoAccountCreationWorksWhenUsingAliasOfDeletedAccount")
				.given(
						newKeyNamed("aliasKey"),
						cryptoCreate("payer2").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer2", "aliasKey", ONE_HUNDRED_HBARS)).via("txn"),
						getTxnRecord("txn").andAllChildRecords().hasChildRecordCount(1).logged()
				).then(
						cryptoDelete("aliasKey").transfer("payer2").hasKnownStatus(SUCCESS).signedBy("aliasKey")
//						getAccountBalance(alias).hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID)
//						getTxnRecord("AutoAccountCreateTxn").hasChildRecordCount(1).logged(),
//						cryptoTransfer(tinyBarsFromTo("payer2", alias, ONE_HUNDRED_HBARS)).via("transferTxn"),
//						getAccountInfo(valid25519Alias).has(
//								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.01))
				);
	}

	private HapiApiSpec transferFromAliasToAlias() {
		final var alias = randomValidEd25519Alias();
		final var aliasToTransfer = randomValidEd25519Alias();
		return defaultHapiSpec("transferFromAliasToAlias")
				.given(
						cryptoCreate("payer4").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer4", alias, 2 * ONE_HUNDRED_HBARS)).via("txn"),
						getTxnRecord("txn").andAllChildRecords().logged(),
						getAccountInfo(alias).has(accountWith().balance((2 * ONE_HUNDRED_HBARS)))
				).then(
						cryptoTransfer(tinyBarsFromTo(alias, aliasToTransfer, ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().logged(),
						getAccountInfo(alias).has(
								accountWith().balance(ONE_HUNDRED_HBARS)),
						getAccountInfo(aliasToTransfer).has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 1, 10))
				);
	}

	private HapiApiSpec transferToAccountAutoCreatedUsingAccount() {
		final var alias = randomValidEd25519Alias();
		return defaultHapiSpec("transferToAccountAutoCreatedUsingAccount")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)).via("txn"),
						getTxnRecord("txn").andAllChildRecords().logged()
				).then(
						withOpContext((spec, opLog) -> {
							final var aliasAccount = spec.registry().getAccountID(alias.toStringUtf8());

							final var op = cryptoTransfer(
									tinyBarsFromTo("payer", asAccountString(aliasAccount), ONE_HUNDRED_HBARS))
									.via("transferTxn2");
							final var op2 = getTxnRecord("transferTxn2").andAllChildRecords().logged();
							final var op3 = getAccountInfo("payer").has(
									accountWith().balance((initialBalance * ONE_HBAR) - (2 * ONE_HUNDRED_HBARS)));
							final var op4 = getAccountInfo(alias).has(
									accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.1));
							CustomSpecAssert.allRunFor(spec, op, op2, op3, op4);
						}));

	}

	private HapiApiSpec transferToAccountAutoCreatedUsingAlias() {
		final var alias = randomValidEd25519Alias();
		return defaultHapiSpec("transferToAccountAutoCreated")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)).via("transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)),
						getAccountInfo(alias).has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1))
				).then(
						cryptoTransfer(tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS))
								.via("transferTxn2").hasKnownStatus(ResponseCodeEnum.INVALID_ACCOUNT_ID)
						// will be modified as below
//						cryptoTransfer(
//								tinyBarsFromTo("payer", alias, ONE_HUNDRED_HBARS)
//						).via("transferTxn2"),
//						getTxnRecord("transferTxn2").andAllChildRecords().logged(),
//						getAccountInfo("payer").has(
//								accountWith().balance((initialBalance * ONE_HBAR) - (2 * ONE_HUNDRED_HBARS))),
//						getAccountInfo(alias).has(
//								accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.1))
				);
	}

	private HapiApiSpec autoAccountCreationUnsupportedAlias() {
		final var threshKeyAlias = Key.newBuilder()
				.setThresholdKey(
						ThresholdKey.newBuilder()
								.setThreshold(2)
								.setKeys(KeyList.newBuilder()
										.addKeys(Key.newBuilder().setEd25519(ByteString.copyFrom("aaa".getBytes())))
										.addKeys(Key.newBuilder().setECDSASecp256K1(
												ByteString.copyFrom("bbbb".getBytes())))
										.addKeys(Key.newBuilder().setEd25519(ByteString.copyFrom("cccccc".getBytes()))))
				)
				.build().toByteString();
		final var keyListAlias = Key.newBuilder().setKeyList(KeyList.newBuilder().addKeys(
						Key.newBuilder().setEd25519(ByteString.copyFrom("aaaaaa".getBytes()))).addKeys(
						Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom("bbbbbbb".getBytes()))))
				.build().toByteString();
		final var contractKeyAlias = Key.newBuilder().setContractID(
				ContractID.newBuilder().setContractNum(100L)).build().toByteString();
		final var delegateContractKeyAlias = Key.newBuilder().setDelegatableContractId(
				ContractID.newBuilder().setContractNum(100L)).build().toByteString();

		return defaultHapiSpec("autoAccountCreationUnsupportedAlias")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", threshKeyAlias, ONE_HUNDRED_HBARS))
								.hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnThreshKey"),
						cryptoTransfer(tinyBarsFromTo("payer", keyListAlias, ONE_HUNDRED_HBARS))
								.hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnKeyList"),
						cryptoTransfer(tinyBarsFromTo("payer", contractKeyAlias, ONE_HUNDRED_HBARS))
								.hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnContract"),
						cryptoTransfer(tinyBarsFromTo("payer", delegateContractKeyAlias, ONE_HUNDRED_HBARS))
								.hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnKeyDelegate")
				).then();
	}


	private HapiApiSpec autoAccountCreationBadAlias() {
		final var invalidAlias = valid25519Alias.substring(0, 10);

		return defaultHapiSpec("AutoAccountCreationBadAlias")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", invalidAlias, ONE_HUNDRED_HBARS))
								.hasPrecheck(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnBad")
				).then();
	}

	private HapiApiSpec autoAccountCreationsHappyPath() {
		return defaultHapiSpec("AutoAccountCreationsHappyPath")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(tinyBarsFromTo("payer", valid25519Alias, ONE_HUNDRED_HBARS)).via("transferTxn")
				).then(
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)
										.noAlias()
						),
						getAccountInfo(valid25519Alias).has(
										accountWith()
												.key(validEd25519Key)
												.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.1)
												.alias(valid25519Alias)
												.autoRenew(THREE_MONTHS_IN_SECONDS)
												.receiverSigReq(false)
												.memo(AUTO_MEMO))
								.logged()
				);
	}

	private HapiApiSpec multipleAutoAccountCreations() {
		final var alias1 = randomValidEd25519Alias();
		final var alias2 = randomValidEd25519Alias();
		final var alias3 = randomValidEd25519Alias();
		final var alias4 = randomValidEd25519Alias();
		final var alias5 = randomValidEd25519Alias();
		return defaultHapiSpec("MultipleAutoAccountCreations")
				.given(
						cryptoCreate("payer").balance(10 * initialBalance * ONE_HBAR)
				)
				.when(
						cryptoTransfer(
								tinyBarsFromTo("payer", alias1, ONE_HUNDRED_HBARS),
								tinyBarsFromTo("payer", alias2, ONE_HUNDRED_HBARS),
								tinyBarsFromTo("payer", alias3, ONE_HUNDRED_HBARS)
						).via("multipleAutoAccountCreates"),
						getTxnRecord("multipleAutoAccountCreates").hasChildRecordCount(3).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - 3 * ONE_HUNDRED_HBARS)
						)
				)
				.then(
						cryptoTransfer(
								tinyBarsFromTo("payer", alias4, ONE_HUNDRED_HBARS),
								tinyBarsFromTo("payer", alias5, 100)
						).via("failedAutoCreate"),
						getTxnRecord("multipleAutoAccountCreates").hasChildRecordCount(0).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - 3 * ONE_HUNDRED_HBARS)
						)
				);
	}
}
