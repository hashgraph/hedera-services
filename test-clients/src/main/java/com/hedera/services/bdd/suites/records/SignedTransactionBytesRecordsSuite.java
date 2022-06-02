package com.hedera.services.bdd.suites.records;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.RECORD_NOT_FOUND;

public class SignedTransactionBytesRecordsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SignedTransactionBytesRecordsSuite.class);

	public static void main(String... args) {
		new SignedTransactionBytesRecordsSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						transactionsWithOnlySigMap()
//						transactionsWithSignedTxnBytesAndSigMap(),
//						transactionsWithSignedTxnBytesAndBodyBytes()
				}
		);
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	private HapiApiSpec transactionsWithOnlySigMap() {
		final var contract = "BalanceLookup";
		return defaultHapiSpec("TransactionsWithOnlySigMap")
				.given(
						cryptoTransfer(tinyBarsFromTo(GENESIS, SYSTEM_ADMIN, 1L))
								.via("failedCryptoTransaction")
								.asTxnWithOnlySigMap()
								.hasPrecheck(INVALID_TRANSACTION_BODY),
						uploadInitCode(contract),
						fileUpdate(contract)
								.via("failedFileTransaction")
								.asTxnWithOnlySigMap()
								.hasPrecheck(INVALID_TRANSACTION_BODY)
				).when(
						contractCreate(contract)
								.balance(1_000L)
								.via("failedContractTransaction")
								.asTxnWithOnlySigMap()
								.hasPrecheck(INVALID_TRANSACTION_BODY)
				).then(
						getTxnRecord("failedCryptoTransaction").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
						getTxnRecord("failedFileTransaction").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID),
						getTxnRecord("failedContractTransaction").hasCostAnswerPrecheck(INVALID_ACCOUNT_ID)
				);
	}

	private HapiApiSpec transactionsWithSignedTxnBytesAndSigMap() {
		return defaultHapiSpec("TransactionsWithSignedTxnBytesAndSigMap")
				.given(
				).when(
						createTopic("testTopic")
								.via("failedConsensusTransaction")
								.asTxnWithSignedTxnBytesAndSigMap()
								.hasPrecheck(INVALID_TRANSACTION)
				).then(
						getTxnRecord("failedConsensusTransaction").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND)
				);
	}


	private HapiApiSpec transactionsWithSignedTxnBytesAndBodyBytes() {
		return defaultHapiSpec("TransactionsWithSignedTxnBytesAndBodyBytes")
				.given(
				).when(
						cryptoCreate("testAccount")
								.via("failedCryptoTransaction")
								.asTxnWithSignedTxnBytesAndBodyBytes()
								.hasPrecheck(INVALID_TRANSACTION)
				).then(
						getTxnRecord("failedCryptoTransaction").hasAnswerOnlyPrecheck(RECORD_NOT_FOUND)
				);
	}
	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
