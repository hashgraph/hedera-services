package com.hedera.services.bdd.suites.records;

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

import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.fee.FeeBuilder;
import com.hedera.services.bdd.spec.HapiApiSpec;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultFailingHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;

import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;

public class CharacterizationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CharacterizationSuite.class);

	final String PATH_TO_VERBOSE_CONTRACT_BYTECODE = "src/main/resource/testfiles/VerboseDeposit.bin";
	final String VERBOSE_DEPOSIT = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"amount\"," +
			"\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"timesForEmphasis\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"string\",\"name\":\"memo\",\"type\":\"string\"}],\"name\":\"deposit\"," +
			"\"outputs\":[{\"internalType\":\"string\",\"name\":\"\",\"type\":\"string\"}],\"payable\":true," +
			"\"stateMutability\":\"payable\",\"type\":\"function\"}";

	public static void main(String... args) {
		new CharacterizationSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
//				payerThresholdFeeIsIncludedInRecordTotal()
//				triplicateThresholdRecordsGenerated()
				resultSizeAffectsFees()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
		);
	}

	private HapiApiSpec resultSizeAffectsFees() {
		final long TRANSFER_AMOUNT = 1_000L;
		BiConsumer<TransactionRecord, Logger> RESULT_SIZE_FORMATTER = (record, txnLog) -> {
			ContractFunctionResult result = record.getContractCallResult();
			txnLog.info("Contract call result FeeBuilder size = "
					+ FeeBuilder.getContractFunctionSize(result)
					+ ", fee = " + record.getTransactionFee()
					+ ", result is [self-reported size = " + result.getContractCallResult().size()
					+ ", '" + result.getContractCallResult() + "']");
			txnLog.info("  Literally :: " + result.toString());
		};

		return defaultHapiSpec("ResultSizeAffectsFees")
				.given(
						TxnVerbs.fileCreate("bytecode").path(PATH_TO_VERBOSE_CONTRACT_BYTECODE),
						TxnVerbs.contractCreate("testContract").bytecode("bytecode")
				).when(
						TxnVerbs.contractCall(
								"testContract", VERBOSE_DEPOSIT,
								TRANSFER_AMOUNT, 0, "So we out-danced thought...")
								.via("noLogsCallTxn").sending(TRANSFER_AMOUNT),
						TxnVerbs.contractCall(
								"testContract", VERBOSE_DEPOSIT,
								TRANSFER_AMOUNT, 5, "So we out-danced thought...")
								.via("loggedCallTxn").sending(TRANSFER_AMOUNT)

				).then(
						assertionsHold((spec, assertLog) -> {
							HapiGetTxnRecord noLogsLookup =
									QueryVerbs.getTxnRecord("noLogsCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							HapiGetTxnRecord logsLookup =
									QueryVerbs.getTxnRecord("loggedCallTxn").loggedWith(RESULT_SIZE_FORMATTER);
							allRunFor(spec, noLogsLookup, logsLookup);
							TransactionRecord unloggedRecord =
									noLogsLookup.getResponse().getTransactionGetRecord().getTransactionRecord();
							TransactionRecord loggedRecord =
									logsLookup.getResponse().getTransactionGetRecord().getTransactionRecord();
							assertLog.info("Fee for logged record   = " + loggedRecord.getTransactionFee());
							assertLog.info("Fee for unlogged record = " + unloggedRecord.getTransactionFee());
							Assert.assertNotEquals(
									"Result size should change the txn fee!",
									unloggedRecord.getTransactionFee(),
									loggedRecord.getTransactionFee());
						})
				);
	}

	private HapiApiSpec payerThresholdFeeIsIncludedInRecordTotal() {
		final long INITIAL_BALANCE = 500_000L;
		final long LOW_SEND_THRESHOLD = 1_000L;

		return defaultFailingHapiSpec("PayerThresholdFeeIncludedInRecordTotal")
				.given(
						cryptoCreate("payer")
								.balance(INITIAL_BALANCE)
								.sendThreshold(LOW_SEND_THRESHOLD)
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, NODE, 1_000L)
						).payingWith("payer").via("transferTxn")
				).then(
						QueryVerbs.getAccountRecords("payer").has(
								inOrder(
										recordWith().fee(spec -> {
											HapiGetAccountBalance op = QueryVerbs.getAccountBalance("payer");
											allRunFor(spec, op);
											long balance = op.getResponse().getCryptogetAccountBalance().getBalance();
											return (INITIAL_BALANCE - balance);
										})
								)
						).logged()
				);
	}

	private HapiApiSpec triplicateThresholdRecordsGenerated() {
		final long INITIAL_BALANCE = 500_000L;
		final long LOW_SEND_THRESHOLD = 1L;

		return defaultFailingHapiSpec("TriplicateThresholdRecordsGenerated")
				.given(
						cryptoCreate("payer")
								.balance(INITIAL_BALANCE)
								.sendThreshold(LOW_SEND_THRESHOLD)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", NODE, 1_000L)
						).payingWith("payer").via("transferTxn")
				).then(
						QueryVerbs.getAccountRecords("payer").has(
								inOrder(
										recordWith(), recordWith(), recordWith()
								)
						).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
