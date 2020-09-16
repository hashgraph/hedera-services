package com.hedera.services.bdd.suites.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.bdd.spec.HapiApiSpec;

import java.util.List;

import com.hedera.services.bdd.spec.assertions.AssertUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.including;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class CryptoGetRecordsRegression extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(CryptoGetRecordsRegression.class);

	public static void main(String... args) {
		new CryptoGetRecordsRegression().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						failsForDeletedAccount(),
//						failsForMissingAccount(),
//						failsForMissingPayment(),
//						failsForInsufficientPayment(),
//						failsForMalformedPayment(),
//						failsForUnfundablePayment(),
//						succeedsNormally(),
						getAccountRecords_testForDuplicates()
				}
		);
	}

	private HapiApiSpec succeedsNormally() {
		String memo = "Dim galleries, dusky corridors got past...";

		return defaultHapiSpec("SucceedsNormally")
				.given(
						cryptoCreate("misc"),
						cryptoCreate("lowThreshPayer").sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo(GENESIS, "misc", 1))
								.payingWith("lowThreshPayer")
								.memo(memo)
								.via("txn")
				).then(
						getAccountRecords("lowThreshPayer").has(AssertUtils.inOrder(
								recordWith()
										.txnId("txn")
										.memo(memo)
										.transfers(including(tinyBarsFromTo(GENESIS, "misc", 1L)))
										.status(SUCCESS)
										.payer("lowThreshPayer")))
				);
	}

	private HapiApiSpec failsForMissingAccount() {
		return defaultHapiSpec("FailsForMissingAccount")
				.given().when().then(
						getAccountRecords("1.2.3")
								.hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
						getAccountRecords("1.2.3")
								.nodePayment(123L)
								.hasAnswerOnlyPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec failsForMalformedPayment() {
		return defaultHapiSpec("FailsForMalformedPayment")
				.given(
						newKeyNamed("wrong").shape(SIMPLE)
				).when().then(
						getAccountRecords(GENESIS)
								.signedBy("wrong")
								.hasAnswerOnlyPrecheck(INVALID_SIGNATURE)
				);
	}

	private HapiApiSpec failsForUnfundablePayment() {
		long everything = 1_234L;
		return defaultHapiSpec("FailsForUnfundablePayment")
				.given(
						cryptoCreate("brokePayer").balance(everything)
				).when().then(
						getAccountRecords(GENESIS)
								.payingWith("brokePayer")
								.nodePayment(everything)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec failsForInsufficientPayment() {
		return defaultHapiSpec("FailsForInsufficientPayment")
				.given().when().then(
						getAccountRecords(GENESIS)
								.nodePayment(1L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec failsForMissingPayment() {
		return defaultHapiSpec("FailsForMissingPayment")
				.given().when().then(
						getAccountRecords(GENESIS)
								.useEmptyTxnAsAnswerPayment()
								.hasAnswerOnlyPrecheck(NOT_SUPPORTED)
				);
	}

	private HapiApiSpec failsForDeletedAccount() {
		return defaultHapiSpec("FailsForDeletedAccount")
				.given(
						cryptoCreate("toBeDeleted")
				).when(
						cryptoDelete("toBeDeleted").transfer(GENESIS)
				).then(
						getAccountRecords("toBeDeleted")
								.hasCostAnswerPrecheck(ACCOUNT_DELETED),
						getAccountRecords("toBeDeleted")
								.nodePayment(123L)
								.hasAnswerOnlyPrecheck(ACCOUNT_DELETED)
				);
	}

	private HapiApiSpec getAccountRecords_testForDuplicates() {
		return defaultHapiSpec("testForDuplicateAccountRecords")
				.given(
						cryptoCreate("account1")
								.balance(5000000000000L)
								.sendThreshold(1L),
						cryptoCreate("account2")
								.balance(5000000000000L)
								.sendThreshold(1L)
				).when(
						cryptoTransfer(tinyBarsFromTo("account1", "account2", 10L))
								.payingWith("account1")
								.via("thresholdTxn")
				)
				.then(
						getAccountRecords("account1").logged()
				);
	}
}
