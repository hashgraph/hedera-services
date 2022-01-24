/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.bdd.suites.misc;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.CREATE_CHILD_ABI;
import static com.hedera.services.bdd.spec.infrastructure.meta.ContractResources.GET_CHILD_RESULT_ABI;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbsWithAlias.getAliasedAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class TransactionIDSuiteWithAlias extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(TransactionIDSuiteWithAlias.class);

	public static void main(String... args) {
		TransactionIDSuiteWithAlias suite = new TransactionIDSuiteWithAlias();
		suite.runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	private List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
				cryptoWithInvalidAliasTxnPayer(),
				consensusWithInvalidAliasAsPayer(),
				schedulesWithInvalidAliasAsPayer(),
				tokensWithInvalidAliasAsPayer(),
				contractsWithInvalidAliasAsPayer()
		);
	}

	private List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(cryptoWithAliasTxnPayer(),
				consensusWithAliasAsPayer(),
				schedulesWithAliasAsPayer(),
				tokensWithAliasAsPayer(),
				contractsWithAliasAsPayer());
	}

	private HapiApiSpec schedulesWithInvalidAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("schedulesWithAliasAsPayer")
				.given(
						newKeyNamed(alias)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						newKeyNamed("admin"),
						scheduleCreate("validScheduledTxn",
								cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
								.designatingPayer("receiver")
								.adminKey("admin")
								.via("scheduleCreateTxn")
								.savingExpectedScheduledTxnId(),
						scheduleCreate("validScheduledTxn1",
								cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
								.designatingPayer(alias)
								.adminKey("admin")
								.payingWith(alias).via("scheduleCreateTxn1")
								.savingExpectedScheduledTxnId()
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getScheduleInfo("validScheduledTxn")
								.payingWith(alias)
								.hasScheduledTxnIdSavedBy("validScheduledTxn")
								.hasAnswerOnlyPrecheck(PAYER_ACCOUNT_NOT_FOUND)
								.logged(),
						scheduleSign("validScheduledTxn")
								.alsoSigningWith(DEFAULT_PAYER, "sender")
								.payingWith(alias)
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)
								.via("scheduleSignTxn"),
						scheduleDelete("validScheduledTxn")
								.payingWith(alias)
								.signedBy(DEFAULT_PAYER, "admin", alias)
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)
								.via("scheduleDelete")
				)
				.then(
						getTxnRecord("scheduleCreateTxn").payingWith(alias).hasPayerAliasNum(alias)
								.logged().hasCostAnswerPrecheck(PAYER_ACCOUNT_NOT_FOUND)
				);
	}

	private HapiApiSpec tokensWithInvalidAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("tokensWithAliasAsPayer")
				.given(
						newKeyNamed("key").shape(SIMPLE),
						newKeyNamed("adminKey").shape(listOf(3)),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("supplyKey"),
						newKeyNamed("wipeKey"),
						cryptoCreate(TOKEN_TREASURY)
								.key("key")
								.balance(1_000 * ONE_HBAR),
						cryptoCreate("autoRenewAccount")
								.key("adminKey")
								.balance(0L),
						cryptoCreate("testAccountA")
								.key("adminKey"),

						newKeyNamed(alias)
				)
				.when(
						tokenCreate("testToken")
								.name("testCoin")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.initialSupply(500)
								.decimals(1)
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.kycKey("kycKey")
								.supplyKey("supplyKey")
								.wipeKey("wipeKey")
								.via("TokenCreate"),
						tokenCreate("primary1")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
								.adminKey("adminKey")
								.payingWith(alias)
								.via("TokenCreate").logged()
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenUpdate("testToken")
								.payingWith(alias)
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)
								.via("TokenUpdate"),
						mintToken("testToken", 1)
								.blankMemo()
								.payingWith(alias)
								.via("MintToken")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						burnToken("testToken", 1)
								.blankMemo()
								.payingWith(alias)
								.via("BurnToken")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenAssociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenAssociation")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						revokeTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenRevokeKyc")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						grantTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenGrantKyc")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenFreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenFreeze")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenUnfreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenUnFreeze")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						cryptoTransfer(moving(1, "testToken")
								.between(TOKEN_TREASURY, "testAccountA"))
								.blankMemo()
								.payingWith(alias)
								.via("TokenTransfer")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						wipeTokenAccount("testToken", "testAccountA", 1)
								.payingWith(alias)
								.blankMemo()
								.via("TokenWipe")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenDissociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenDissociation")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getTokenInfo("testToken")
								.payingWith(alias)
								.via("TokenGetInfo").logged()
								.hasAnswerOnlyPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						tokenDelete("testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenDelete")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)
				)
				.then(
						getTxnRecord("TokenCreate").payingWith(alias).hasPayerAliasNum(alias)
								.logged().hasCostAnswerPrecheck(INVALID_PAYER_ACCOUNT_ID)
				);
	}

	private HapiApiSpec consensusWithInvalidAliasAsPayer() {
		final var alias = "alias";
		return defaultHapiSpec("consensusWithInvalidAliasAsPayer")
				.given(
						newKeyNamed(alias),
						newKeyNamed("adminKey"),
						createTopic("testTopic").adminKeyName("adminKey")
				).when(
						createTopic("topic").adminKeyName("adminKey").payingWith(alias)
								.via("createTopicTxn").hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						updateTopic("testTopic").memo("updated-topic-memo").payingWith(alias)
								.via("updateTopicTxn").hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getTopicInfo("testTopic").payingWith(alias).logged()
								.hasAnswerOnlyPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						submitMessageTo("testTopic").payingWith(alias).via("submitMessage")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						deleteTopic("testTopic").signedBy("adminKey", alias).payingWith(alias)
								.via("topicDelete").hasPrecheck(PAYER_ACCOUNT_NOT_FOUND)
				).then(
						getTxnRecord("createTopicTxn").payingWith(alias).hasPayerAliasNum(alias).logged()
								.hasCostAnswerPrecheck(INVALID_PAYER_ACCOUNT_ID)
				);
	}


	private HapiApiSpec cryptoWithInvalidAliasTxnPayer() {
		final var alias = "alias";
		final var receiver = "receiver";
		return defaultHapiSpec("cryptoCreateWithAliasTxnPayer")
				.given(
						newKeyNamed(alias),
						cryptoCreate(receiver).balance(ONE_HUNDRED_HBARS)
				).when(
						cryptoCreate("test").payingWith(alias).via("createTxn")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						cryptoUpdate(receiver).memo("updated-memo").payingWith(alias).via("updateTxn")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						cryptoTransfer(tinyBarsFromToWithAlias(receiver, alias, 10))
								.payingWith(alias).via("transferTxn")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getAccountBalance(receiver).payingWith(alias).logged(),
						getAccountInfo(receiver).payingWith(alias)
								.logged().hasAnswerOnlyPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getAccountRecords(receiver).payingWith(alias)
								.logged().hasAnswerOnlyPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						cryptoDelete(receiver).payingWith(alias).via("deleteTxn")
								.hasPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getAliasedAccountRecords(alias).payingWith(alias)
								.logged().hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)
				).then(
						getTxnRecord("createTxn").payingWith(alias)
								.hasPayerAliasNum(alias).logged()
								.hasCostAnswerPrecheck(INVALID_PAYER_ACCOUNT_ID)
				);
	}

	private HapiApiSpec contractsWithInvalidAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("contractsWithAliasAsPayer")
				.given(
						newKeyNamed(alias),
						newKeyNamed("adminKey"),

						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(10_000_000_000L),
						fileCreate("contractFile")
								.payingWith("payer")
								.fromResource("contract/bytecodes/CreateTrivial.bin")
				).when(
						contractCreate("testContract")
								.bytecode("contractFile")
								.adminKey("key")
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS - 1)
								.gas(30000)
								.payingWith(alias)
								.hasKnownStatus(SUCCESS)
								.via("ContractCreate")
								.hasKnownStatus(PAYER_ACCOUNT_NOT_FOUND),
						contractUpdate("testContract")
								.payingWith(alias)
								.newKey("key")
								.newMemo("testContract")
								.via("ContractUpdate")
								.hasKnownStatus(PAYER_ACCOUNT_NOT_FOUND),
						contractCall("testContract", CREATE_CHILD_ABI)
								.hasKnownStatus(PAYER_ACCOUNT_NOT_FOUND)
								.payingWith(alias)
								.gas(100000)
								.via("ContractCall"),
						getContractInfo("testContract")
								.payingWith(alias)
								.via("GetContractInfo").logged()
								.hasCostAnswerPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						contractCallLocal("testContract", GET_CHILD_RESULT_ABI)
								.payingWith(alias)
								.nodePayment(100_000_000)
								.gas(50000)
								.via("ContractCallLocal")
								.hasCostAnswerPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getContractBytecode("testContract")
								.payingWith(alias)
								.via("GetContractByteCode")
								.hasCostAnswerPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						getContractRecords("testContract")
								.payingWith(alias)
								.logged()
								.via("GetContractRecords")
								.hasCostAnswerPrecheck(PAYER_ACCOUNT_NOT_FOUND),
						contractDelete("testContract")
								.payingWith(alias)
								.via("ContractDelete")
								.hasKnownStatus(PAYER_ACCOUNT_NOT_FOUND)

				).then(
				);
	}


	private HapiApiSpec schedulesWithAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("schedulesWithAliasAsPayer")
				.given(
						newKeyNamed(alias),
						cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, alias, ONE_HUNDRED_HBARS)).via(
								autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						cryptoCreate("sender"),
						cryptoCreate("receiver"),
						newKeyNamed("admin"),
						scheduleCreate("validScheduledTxn",
								cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1)))
								.designatingPayer(alias)
								.adminKey("admin")
								.payingWith(alias).via("scheduleCreateTxn")
								.savingExpectedScheduledTxnId(),
						getScheduleInfo("validScheduledTxn")
								.hasPayerAccountID(alias)
								.hasScheduledTxnIdSavedBy("validScheduledTxn").logged(),
						scheduleSign("validScheduledTxn")
								.alsoSigningWith(DEFAULT_PAYER, "sender")
								.payingWith(alias)
								.hasKnownStatus(SUCCESS)
								.via("scheduleSignTxn"),
						scheduleDelete("validScheduledTxn")
								.payingWith(alias)
								.signedBy(DEFAULT_PAYER, "admin", alias)
								.hasKnownStatus(SCHEDULE_ALREADY_EXECUTED)
								.via("scheduleDelete")
				)
				.then(
						getTxnRecord("scheduleCreateTxn").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("scheduleSignTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("scheduleDelete").hasPayerAliasNum(alias).logged()
				);
	}

	private HapiApiSpec tokensWithAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("tokensWithAliasAsPayer")
				.given(
						newKeyNamed("key").shape(SIMPLE),
						newKeyNamed("adminKey").shape(listOf(3)),
						newKeyNamed("freezeKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("supplyKey"),
						newKeyNamed("wipeKey"),
						cryptoCreate(TOKEN_TREASURY)
								.key("key")
								.balance(1_000 * ONE_HBAR),
						cryptoCreate("autoRenewAccount")
								.key("adminKey")
								.balance(0L),
						cryptoCreate("testAccountA")
								.key("adminKey"),

						newKeyNamed(alias),
						cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, alias, ONE_HUNDRED_HBARS)).via(
								autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				)
				.when(
						tokenCreate("primary")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
								.adminKey("adminKey")
								.payingWith(alias)
								.via("TokenCreate").logged(),
						tokenUpdate("primary")
								.payingWith(alias)
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.via("TokenUpdate"),
						tokenCreate("testToken")
								.name("testCoin")
								.treasury(TOKEN_TREASURY)
								.autoRenewAccount("autoRenewAccount")
								.autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
								.initialSupply(500)
								.decimals(1)
								.adminKey("adminKey")
								.freezeKey("freezeKey")
								.kycKey("kycKey")
								.supplyKey("supplyKey")
								.wipeKey("wipeKey"),
						mintToken("testToken", 1)
								.blankMemo()
								.payingWith(alias)
								.via("MintToken"),
						burnToken("testToken", 1)
								.blankMemo()
								.payingWith(alias)
								.via("BurnToken"),
						tokenAssociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenAssociation"),
						revokeTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenRevokeKyc"),
						grantTokenKyc("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenGrantKyc"),
						tokenFreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenFreeze"),
						tokenUnfreeze("testToken", "testAccountA")
								.blankMemo()
								.payingWith(alias)
								.via("TokenUnFreeze"),
						cryptoTransfer(moving(1, "testToken")
								.between(TOKEN_TREASURY, "testAccountA"))
								.blankMemo()
								.payingWith(alias)
								.via("TokenTransfer"),
						wipeTokenAccount("testToken", "testAccountA", 1)
								.payingWith(alias)
								.blankMemo()
								.via("TokenWipe"),
						tokenDissociate("testAccountA", "testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenDissociation"),
						getTokenInfo("testToken")
								.payingWith(alias)
								.via("TokenGetInfo").logged(),
						tokenDelete("testToken")
								.blankMemo()
								.payingWith(alias)
								.via("TokenDelete")
				)
				.then(
						getTxnRecord("TokenCreate").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenUpdate").hasPayerAliasNum(alias).logged(),
						getTxnRecord("MintToken").hasPayerAliasNum(alias).logged(),
						getTxnRecord("BurnToken").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenAssociation").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenRevokeKyc").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenGrantKyc").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenFreeze").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenUnFreeze").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenTransfer").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenWipe").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenDissociation").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenDelete").hasPayerAliasNum(alias).logged(),
						getTxnRecord("TokenGetInfo").hasPayerAliasNum(alias).logged(),
						getReceipt("TokenDelete").payingWith(alias).logged()
				);
	}

	private HapiApiSpec consensusWithAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("consensusWithAliasAsPayer")
				.given(
						newKeyNamed(alias),
						newKeyNamed("adminKey"),
						cryptoTransfer(tinyBarsFromToWithAlias(GENESIS, alias, ONE_HUNDRED_HBARS)).via(autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						createTopic("topic").adminKeyName("adminKey").payingWith(alias).via("createTopicTxn"),
						updateTopic("topic").memo("updated-topic-memo").payingWith(alias).via("updateTopicTxn"),
						getTopicInfo("topic").payingWith(alias).logged(),
						submitMessageTo("topic").payingWith(alias).via("submitMessage"),
						deleteTopic("topic").signedBy("adminKey", alias).payingWith(alias).via("topicDelete")
				).then(
						getTxnRecord("createTopicTxn").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("updateTopicTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("submitMessage").hasPayerAliasNum(alias).logged(),
						getTxnRecord("topicDelete").hasPayerAliasNum(alias).logged(),
						getReceipt("createTopicTxn").payingWith(alias).logged()
				);
	}


	private HapiApiSpec cryptoWithAliasTxnPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		final var receiver = "receiver";
		return defaultHapiSpec("cryptoCreateWithAliasTxnPayer")
				.given(
						newKeyNamed(alias),
						cryptoCreate(receiver),
						cryptoTransfer(tinyBarsFromToWithAlias(GENESIS, alias, ONE_HUNDRED_HBARS)).via(autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						cryptoCreate("test").payingWith(alias).via("createTxn"),
						cryptoUpdate("test").memo("updated-memo").payingWith(alias).via("updateTxn"),
						cryptoTransfer(tinyBarsFromToWithAlias("test", alias, 10)).payingWith(alias).via("transferTxn"),
						getAccountBalance("test").payingWith(alias).via("accountBalance"),
						getAccountInfo("test").payingWith(alias).via("accountInfo").logged(),
						cryptoDelete("test").payingWith(alias).via("deleteTxn"),
						getAliasedAccountRecords(alias).payingWith(alias).via("getAccountRecords").logged()
				).then(
						getTxnRecord("createTxn").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("updateTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("transferTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("deleteTxn").hasPayerAliasNum(alias).logged(),
						getReceipt("createTxn").payingWith(alias).logged()
				);
	}

	private HapiApiSpec contractsWithAliasAsPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		return defaultHapiSpec("contractsWithAliasAsPayer")
				.given(
						newKeyNamed(alias),
						newKeyNamed("adminKey"),
						cryptoTransfer(tinyBarsFromToWithAlias(GENESIS, alias, ONE_HUNDRED_HBARS)).via(autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1),

						newKeyNamed("key").shape(SIMPLE),
						cryptoCreate("payer")
								.key("key")
								.balance(10_000_000_000L),
						fileCreate("contractFile")
								.payingWith("payer")
								.fromResource("contract/bytecodes/CreateTrivial.bin")
				).when(
						contractCreate("testContract")
								.bytecode("contractFile")
								.adminKey("key")
								.autoRenewSecs(THREE_MONTHS_IN_SECONDS - 1)
								.gas(30000)
								.payingWith(alias)
								.hasKnownStatus(SUCCESS)
								.via("ContractCreate"),
						contractUpdate("testContract")
								.payingWith(alias)
								.newKey("key")
								.newMemo("testContract")
								.via("ContractUpdate"),
						contractCall("testContract", CREATE_CHILD_ABI)
								.payingWith(alias)
								.gas(100000)
								.via("ContractCall"),
						getContractInfo("testContract")
								.payingWith(alias)
								.via("GetContractInfo").logged(),
						contractCallLocal("testContract", GET_CHILD_RESULT_ABI)
								.payingWith(alias)
								.nodePayment(100_000_000)
								.gas(50000)
								.via("ContractCallLocal"),
						getContractBytecode("testContract")
								.payingWith(alias)
								.via("GetContractByteCode"),
						getContractRecords("testContract")
								.payingWith(alias)
								.logged()
								.via("GetContractRecords"),
						contractDelete("testContract")
								.payingWith(alias)
								.via("ContractDelete")

				).then(
						getTxnRecord("ContractCreate").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("ContractUpdate").hasPayerAliasNum(alias).logged(),
						getTxnRecord("ContractCall").hasPayerAliasNum(alias).logged(),
						getTxnRecord("GetContractInfo").hasPayerAliasNum(alias).logged(),
						getTxnRecord("ContractCallLocal").hasPayerAliasNum(alias).logged(),
						getTxnRecord("GetContractByteCode").hasPayerAliasNum(alias).logged(),
						getTxnRecord("GetContractRecords").hasPayerAliasNum(alias).logged(),
						getTxnRecord("ContractDelete").hasPayerAliasNum(alias).logged()
				);
	}


	@Override
	public boolean canRunAsync() {
		return true;
	}
}
