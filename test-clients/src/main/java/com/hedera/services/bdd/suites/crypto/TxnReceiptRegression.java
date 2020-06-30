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

import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECEIPT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

public class TxnReceiptRegression extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(TxnReceiptRegression.class);

	public static void main(String... args) {
		new TxnReceiptRegression().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						returnsInvalidForUnspecifiedTxnId(),
						returnsNotSupportedForMissingOp(),
						receiptAvailableWithinCacheTtl(),
//						receiptUnavailableAfterCacheTtl(),
						receiptUnavailableIfRejectedInPrecheck(),
						receiptNotFoundOnUnknownTransactionID(),
						receiptUnknownBeforeConsensus(),
				}
		);
	}

	private HapiApiSpec returnsInvalidForUnspecifiedTxnId() {
		return defaultHapiSpec("ReturnsInvalidForUnspecifiedTxnId")
				.given( ).when( ).then(
						getReceipt("")
								.useDefaultTxnId()
								.hasAnswerOnlyPrecheck(INVALID_TRANSACTION_ID)
				);
	}

	private HapiApiSpec returnsNotSupportedForMissingOp() {
		return defaultHapiSpec("ReturnsNotSupportedForMissingOp")
				.given(
						cryptoCreate("misc").via("success").balance(1_000L)
				).when( ).then(
						getReceipt("success")
								.forgetOp()
								.hasAnswerOnlyPrecheck(NOT_SUPPORTED)
				);
	}

	private HapiApiSpec receiptUnavailableAfterCacheTtl() {
		return defaultHapiSpec("ReceiptUnavailableAfterCacheTtl")
				.given(
						cryptoCreate("misc").via("success").balance(1_000L)
				).when(
						sleepFor(200_000L)
				).then(
						getReceipt("success").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND)
				);
	}

	private HapiApiSpec receiptUnknownBeforeConsensus() {
		return defaultHapiSpec("ReceiptUnknownBeforeConsensus")
				.given(
				).when( ).then(
						cryptoCreate("misc")
								.via("success")
								.balance(1_000L)
								.deferStatusResolution(),
						getReceipt("success").hasReceiptStatus(UNKNOWN)
				);
	}

	private HapiApiSpec receiptAvailableWithinCacheTtl() {
		return defaultHapiSpec("ReceiptAvailableWithinCacheTtl")
				.given(
						cryptoCreate("misc").via("success").balance(1_000L)
				).when( ).then(
						getReceipt("success").hasReceiptStatus(SUCCESS)
				);
	}

	private HapiApiSpec receiptUnavailableIfRejectedInPrecheck() {
		return defaultHapiSpec("ReceiptUnavailableIfRejectedInPrecheck")
				.given(
						usableTxnIdNamed("failingTxn"),
						cryptoCreate("misc").balance(1_000L)
				).when(
						cryptoCreate("nope")
								.payingWith("misc")
								.hasPrecheck(INSUFFICIENT_PAYER_BALANCE)
								.txnId("failingTxn")
				).then(
						getReceipt("failingTxn").hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND)
				);
	}

	private HapiApiSpec receiptNotFoundOnUnknownTransactionID() {
		return defaultHapiSpec("receiptNotFoundOnUnknownTransactionID")
				.given( ).when( ).then(
						withOpContext((spec, ctxLog) -> {
							HapiGetReceipt op = getReceipt(spec.txns().defaultTransactionID())
									.hasAnswerOnlyPrecheck(RECEIPT_NOT_FOUND);
							CustomSpecAssert.allRunFor(spec, op);
							})
				);
	}
}
