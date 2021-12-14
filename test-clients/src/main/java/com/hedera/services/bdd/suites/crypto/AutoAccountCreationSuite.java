package com.hedera.services.bdd.suites.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfoWithAlias;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromAccountToAlias;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.crypto.AutoCreateUtils.randomValidEd25519Alias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
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
						autoAccountCreationsHappyPath(),
						autoAccountCreationBadAlias(),
						autoAccountCreationUnsupportedAlias(),
						transferToAccountAutoCreatedUsingAlias(),
						transferToAccountAutoCreatedUsingAccount(),
						transferFromAliasToAlias(),
						multipleAutoAccountCreations(),
						accountCreatedIfAliasUsedAsPubKey(),
						aliasCanBeUsedOnManyAccountsNotAsAlias(),
						autoAccountCreationWorksWhenUsingAliasOfDeletedAccount(),
						canGetBalanceAndInfoViaAlias(),
				}
		);
	}

	private HapiApiSpec canGetBalanceAndInfoViaAlias() {
		final var ed25519SourceKey = "ed25519Alias";
		final var secp256k1SourceKey = "secp256k1Alias";
		final var secp256k1Shape = KeyShape.SECP256K1;
		final var ed25519Shape = KeyShape.ED25519;
		final var autoCreation = "autoCreation";

		return defaultHapiSpec("CanGetBalanceAndInfoViaAlias")
				.given(
						newKeyNamed(ed25519SourceKey).shape(ed25519Shape),
						newKeyNamed(secp256k1SourceKey).shape(secp256k1Shape)
				).when(
						cryptoTransfer(
								tinyBarsFromAccountToAlias(GENESIS, ed25519SourceKey, ONE_HUNDRED_HBARS),
								tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS)
						)
								.payingWith(GENESIS)
								.via(autoCreation)
				).then(
						getAliasedAccountBalance(ed25519SourceKey),
						getAliasedAccountBalance(secp256k1SourceKey),
						getAliasedAccountInfo(ed25519SourceKey).hasExpectedAliasKey(),
						getAliasedAccountInfo(secp256k1SourceKey).hasExpectedAliasKey()
				);
	}

	private HapiApiSpec aliasCanBeUsedOnManyAccountsNotAsAlias() {
		return defaultHapiSpec("AliasCanBeUsedOnManyAccountsNotAsAlias")
				.given(
						/* have alias key on other accounts and tokens not as alias */
						newKeyNamed("validAlias"),
						cryptoCreate("payer").key("validAlias").balance(initialBalance * ONE_HBAR),
						tokenCreate("payer").adminKey("validAlias"),
						tokenCreate("payer").supplyKey("validAlias"),
						tokenCreate("a").treasury("payer")
				).when(
						/* auto account is created */
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "validAlias",
										ONE_HUNDRED_HBARS)).via(
								"transferTxn")
				).then(
						/* get transaction record and validate the child record has alias bytes as expected */
						getTxnRecord("transferTxn").andAllChildRecords().hasChildRecordCount(
								1).hasAliasInChildRecord("validAlias", 0).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)
										.noAlias()
						),
						getAccountInfoWithAlias("validAlias").has(
								accountWith()
										.key("validAlias")
										.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5)
										.alias("validAlias")
										.autoRenew(THREE_MONTHS_IN_SECONDS)
										.receiverSigReq(false)
										.memo(AUTO_MEMO))
								.logged()
				);
	}

	private HapiApiSpec accountCreatedIfAliasUsedAsPubKey() {
		return defaultHapiSpec("AccountCreatedIfAliasUsedAsPubKey")
				.given(
						newKeyNamed("alias"),
						cryptoCreate("payer1").balance(initialBalance * ONE_HBAR)
								.key("alias")
								.signedBy("alias", DEFAULT_PAYER)
				).when(
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer1", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn")
				).then(
						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer1").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)
										.noAlias()
						),
						getAccountInfo("alias", true).has(
								accountWith()
										.key("alias")
										.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5)
										.alias("alias")
										.autoRenew(THREE_MONTHS_IN_SECONDS)
										.receiverSigReq(false))
								.logged()
				);
	}

	private HapiApiSpec autoAccountCreationWorksWhenUsingAliasOfDeletedAccount() {
		return defaultHapiSpec("deleteAutoCreatedAccount")
				.given(
						newKeyNamed("alias"),
						newKeyNamed("alias2"),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
								"txn"),
						getTxnRecord("txn").hasChildRecordCount(1).logged()
				).then(
						cryptoDelete("alias", true)
								.transfer("payer")
								.hasKnownStatus(SUCCESS)
								.signedBy("alias", "payer", DEFAULT_PAYER)
								.purging(),
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
								"txn2").hasKnownStatus(ACCOUNT_DELETED)

						/* need to validate it creates after expiration */
//						sleepFor(60000L),
//						cryptoTransfer(
//								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
//								"txn2"),
//						getTxnRecord("txn2").hasChildRecordCount(1).logged()
				);
	}

	private HapiApiSpec transferFromAliasToAlias() {
		final var alias = randomValidEd25519Alias();
		final var aliasToTransfer = randomValidEd25519Alias();
		return defaultHapiSpec("transferFromAliasToAlias")
				.given(
						newKeyNamed("alias"),
						newKeyNamed("alias2"),
						cryptoCreate("payer4").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromToWithAlias("payer4", "alias",
								2 * ONE_HUNDRED_HBARS)).via(
								"txn"),
						getTxnRecord("txn").andAllChildRecords().logged(),
						getAccountInfoWithAlias("alias").has(
								accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.5))
				).then(
						/* transfer from an alias that was auto created to a new alias, validate account is created */
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("alias", "alias2", ONE_HUNDRED_HBARS)).via(
								"transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().logged(),
						getAccountInfoWithAlias("alias").has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5)),
						getAccountInfoWithAlias("alias2").has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5))
				);
	}

	private HapiApiSpec transferToAccountAutoCreatedUsingAccount() {
		return defaultHapiSpec("transferToAccountAutoCreatedUsingAccount")
				.given(
						newKeyNamed("transferAlias"),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "transferAlias",
										ONE_HUNDRED_HBARS)).via(
								"txn"),
						getTxnRecord("txn").andAllChildRecords().logged()
				).then(
						/* get the account associated with alias and transfer */
						withOpContext((spec, opLog) -> {
							final var aliasAccount = spec.registry().getAccountID(
									spec.registry().getKey("transferAlias").toByteString().toStringUtf8());

							final var op = cryptoTransfer(
									tinyBarsFromTo("payer", asAccountString(aliasAccount), ONE_HUNDRED_HBARS))
									.via("transferTxn2");
							final var op2 = getTxnRecord("transferTxn2").andAllChildRecords().logged();
							final var op3 = getAccountInfo("payer").has(
									accountWith().balance((initialBalance * ONE_HBAR) - (2 * ONE_HUNDRED_HBARS)));
							final var op4 = getAccountInfoWithAlias("transferAlias").has(
									accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.5));
							CustomSpecAssert.allRunFor(spec, op, op2, op3, op4);
						}));

	}

	private HapiApiSpec transferToAccountAutoCreatedUsingAlias() {
		return defaultHapiSpec("transferToAccountAutoCreated")
				.given(
						newKeyNamed("alias"),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS)).via(
								"transferTxn"),

						getTxnRecord("transferTxn").andAllChildRecords().logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)),
						getAccountInfoWithAlias("alias").has(
								accountWith().expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5))
				).then(
						/* transfer using alias and not account number */
						cryptoTransfer(HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias", ONE_HUNDRED_HBARS))
								.via("transferTxn2"),
						getTxnRecord("transferTxn2").andAllChildRecords().hasChildRecordCount(0).logged(),
						getAccountInfo("payer").has(
								accountWith().balance((initialBalance * ONE_HBAR) - (2 * ONE_HUNDRED_HBARS))),
						getAccountInfoWithAlias("alias").has(
								accountWith().expectedBalanceWithChargedUsd((2 * ONE_HUNDRED_HBARS), 0.05, 0.5))
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
								.hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnThreshKey"),
						cryptoTransfer(tinyBarsFromTo("payer", keyListAlias, ONE_HUNDRED_HBARS))
								.hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnKeyList"),
						cryptoTransfer(tinyBarsFromTo("payer", contractKeyAlias, ONE_HUNDRED_HBARS))
								.hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnContract"),
						cryptoTransfer(tinyBarsFromTo("payer", delegateContractKeyAlias, ONE_HUNDRED_HBARS))
								.hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
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
								.hasKnownStatus(ResponseCodeEnum.INVALID_ALIAS_KEY)
								.via("transferTxnBad")
				).then();
	}

	private HapiApiSpec autoAccountCreationsHappyPath() {
		return defaultHapiSpec("AutoAccountCreationsHappyPath")
				.given(
						newKeyNamed("validAlias"),
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				).when(
						/* auto account is created */
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "validAlias",
										ONE_HUNDRED_HBARS)).via(
								"transferTxn")
				).then(
						/* get transaction record and validate the child record has alias bytes as expected */
						getReceipt("transferTxn")
								.andAnyChildReceipts()
								.logged(),
						getTxnRecord("transferTxn").andAllChildRecords().hasChildRecordCount(
								1).hasAliasInChildRecord("validAlias", 0).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - ONE_HUNDRED_HBARS)
										.noAlias()
						),
						getAccountInfoWithAlias("validAlias").has(
								accountWith()
										.key("validAlias")
										.expectedBalanceWithChargedUsd(ONE_HUNDRED_HBARS, 0.05, 0.5)
										.alias("validAlias")
										.autoRenew(THREE_MONTHS_IN_SECONDS)
										.receiverSigReq(false)
										.memo(AUTO_MEMO))
								.logged()
				);
	}

	private HapiApiSpec multipleAutoAccountCreations() {
		return defaultHapiSpec("MultipleAutoAccountCreations")
				.given(
						cryptoCreate("payer").balance(initialBalance * ONE_HBAR)
				)
				.when(
						newKeyNamed("alias1"),
						newKeyNamed("alias2"),
						newKeyNamed("alias3"),
						newKeyNamed("alias4"),
						newKeyNamed("alias5"),
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias1", ONE_HUNDRED_HBARS),
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias2", ONE_HUNDRED_HBARS),
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias3", ONE_HUNDRED_HBARS)
						).via("multipleAutoAccountCreates"),
						getTxnRecord("multipleAutoAccountCreates").hasChildRecordCount(3).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - 3 * ONE_HUNDRED_HBARS)
						)
				)
				.then(
						cryptoTransfer(
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias4", ONE_HUNDRED_HBARS),
								HapiCryptoTransfer.tinyBarsFromToWithAlias("payer", "alias5", 100)
						).via("failedAutoCreate").hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
						getTxnRecord("failedAutoCreate").hasChildRecordCount(0).logged(),
						getAccountInfo("payer").has(
								accountWith()
										.balance((initialBalance * ONE_HBAR) - 3 * ONE_HUNDRED_HBARS)
						)
				);
	}
}
