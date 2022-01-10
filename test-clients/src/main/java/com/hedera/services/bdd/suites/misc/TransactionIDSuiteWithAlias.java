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
import com.hederahashgraph.api.proto.java.TokenType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.queries.QueryVerbsWithAlias.getAliasedAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbsWithAlias.getAliasedAccountRecords;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.TxnVerbsWithAlias.tokenAssociateAliased;
import static com.hedera.services.bdd.spec.transactions.TxnVerbsWithAlias.tokenDissociateAliased;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
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
		return List.of(new HapiApiSpec[] {
				cryptoWithAliasTxnPayer(),
				consensusWithAliasAsPayer(),
				schedulesWithAliasAsPayer(),
//				tokensWithAliasAsPayer()
// 				contractsWithAliasAsPayer(),
		});
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
		final var token = "token";
		final var civilian = "civilian";
		final var adminKey = "adminKey";
		final var supplyKey = "supplyKey";
		return defaultHapiSpec("tokensWithAliasAsPayer")
				.given(
						newKeyNamed(alias),
						newKeyNamed(adminKey),
						newKeyNamed(supplyKey),
						cryptoCreate(civilian).key(supplyKey),
						cryptoTransfer(tinyBarsFromToWithAlias(DEFAULT_PAYER, alias, ONE_HUNDRED_HBARS)).via(
								autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						tokenCreate(token)
								.adminKey("adminKey")
								.treasury(TOKEN_TREASURY)
								.tokenType(TokenType.FUNGIBLE_COMMON)
								.kycKey("adminKey")
								.freezeKey("adminKey")
								.payingWith(alias)
								.via("tokenCreateTxn"),
						tokenAssociateAliased(alias, token).payingWith(alias).via("tokenAssociationTxn"),
						tokenUpdate(token).entityMemo("new-entity-memo").payingWith(alias).via("tokenUpdateTxn"),
						mintToken(token, 100).payingWith(alias).signedBy(supplyKey).via("mintTokenTxn"),
						burnToken(token, 100).payingWith(alias).via("burnTokenTxn"),
						getAliasedAccountInfo(alias).hasToken(relationshipWith(token)).logged(),
						tokenFreeze(token, alias).signedBy("adminKey", TOKEN_TREASURY).payingWith(alias).via(
								"freezeTxn"),
						tokenUnfreeze(token, alias).payingWith("adminKey").signedBy("adminKey", TOKEN_TREASURY).via(
								"unfreezeTxn"),
						wipeTokenAccount(token, alias, 1L).payingWith(alias).fee(ONE_HBAR).via("wipeTokenTxn"),

						tokenDissociateAliased(alias, token).payingWith(alias).via("tokenDissociateTxn"),
						getTokenInfo(token).payingWith(alias).logged()

				)
				.then(
						getTxnRecord("tokenCreateTxn").payingWith(alias).hasPayerAliasNum(alias).logged(),
						getTxnRecord("tokenAssociationTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("tokenUpdateTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("mintTokenTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("burnTokenTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("freezeTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("unfreezeTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("wipeTokenTxn").hasPayerAliasNum(alias).logged(),
						getTxnRecord("tokenDissociateTxn").hasPayerAliasNum(alias).logged(),
						getReceipt("burnTokenTxn").payingWith(alias).logged()
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
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						contractCreate("contract").adminKey("adminKey").memo("memo").payingWith(alias).via(
								"createContractTxn"),
						contractUpdate("contract").newMemo("secondMemo").payingWith(alias).via("updateContractTxn"),
						getContractInfo("contract").has(contractWith().memo("secondMemo")).payingWith(alias)

				).then(
				);
	}


	@Override
	public boolean canRunAsync() {
		return true;
	}
}
