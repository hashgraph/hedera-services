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

import java.time.Instant;
import java.util.List;

import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.accountWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.SIMPLE;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class CryptoGetInfoRegression extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(CryptoGetInfoRegression.class);

	public static void main(String... args) {
		new CryptoGetInfoRegression().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						failsForDeletedAccount(),
						failsForMissingAccount(),
						failsForMissingPayment(),
						failsForInsufficientPayment(),
						failsForMalformedPayment(),
						failsForUnfundablePayment(),
						succeedsNormally(),
				}
		);
	}

	private HapiApiSpec succeedsNormally() {
		long balance = 1_234_567L;
		long autoRenew = 5_555_555L;
		long sendThresh = 1_111L;
		long receiveThresh = 2_222L;
		long expiry = Instant.now().getEpochSecond() + autoRenew;
		KeyShape misc = listOf(SIMPLE, listOf(2));

		return defaultHapiSpec("SucceedsNormally")
				.given(
						newKeyNamed("misc").shape(misc)
				).when(
						cryptoCreate("target")
								.key("misc")
								.proxy("1.2.3")
								.balance(balance)
								.sendThreshold(sendThresh)
								.receiveThreshold(receiveThresh)
								.receiverSigRequired(true)
								.autoRenewSecs(autoRenew)
				).then(
						getAccountInfo("target")
								.has(accountWith()
										.accountId("target")
										.solidityId("target")
										.proxy("1.2.3")
										.key("misc")
										.balance(balance)
										.sendThreshold(sendThresh)
										.receiveThreshold(receiveThresh)
										.expiry(expiry, 5L)
										.autoRenew(autoRenew)
								).logged()
				);
	}

	private HapiApiSpec failsForMissingAccount() {
		return defaultHapiSpec("FailsForMissingAccount")
				.given().when().then(
						getAccountInfo("1.2.3")
								.hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec failsForMalformedPayment() {
		return defaultHapiSpec("FailsForMalformedPayment")
				.given(
						newKeyNamed("wrong").shape(SIMPLE)
				).when().then(
						getAccountInfo(GENESIS)
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
						getAccountInfo(GENESIS)
								.payingWith("brokePayer")
								.nodePayment(everything)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	private HapiApiSpec failsForInsufficientPayment() {
		return defaultHapiSpec("FailsForInsufficientPayment")
				.given().when().then(
						getAccountInfo(GENESIS)
								.nodePayment(1L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE)
				);
	}

	private HapiApiSpec failsForMissingPayment() {
		return defaultHapiSpec("FailsForMissingPayment")
				.given().when().then(
						getAccountInfo(GENESIS)
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
						getAccountInfo("toBeDeleted")
								.hasCostAnswerPrecheck(ACCOUNT_DELETED)
				);
	}
}
