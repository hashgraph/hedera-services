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

import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/* --------------------------- SPEC STATIC IMPORTS --------------------------- */
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.Assert.assertEquals;
/* --------------------------------------------------------------------------- */

public class ThresholdRecordCreationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ThresholdRecordCreationSuite.class);

	public static void main(String... args) {
		new ThresholdRecordCreationSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						newlyCreatedContractGetsRecord(),
						cacheRecordPersistenceIsAsExpected(),
						successfullyCalledContractGetsRecord(),
						unsuccessfullyCalledContractGetsRecord(),
						onlyNetAdjustmentIsComparedToThresholdWhenCreating(),
						bothSendAndReceiveThresholdsAreConsideredWhenCreating(),
						newlyCreatedAccountReceiveThresholdIsIgnored(),
						neitherSendAndReceiveThresholdsAreConsideredWhenNotCreating(),
				}
		);
	}

	/**
	 * Builds a spec in which we confirm that the cache records are
	 * being added to state in accordance to the policy matching the
	 * environment flag ADD_CACHE_RECORD_TO_STATE.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec cacheRecordPersistenceIsAsExpected() {
		HapiSpecOperation finalClause = cacheRecordsAreAddedToState()
				? getAccountRecords("payer").has(inOrder(recordWith().txnId("transferTxn")))
				: getAccountRecords("payer").has(inOrder());

		return defaultHapiSpec("CacheRecordPersistenceIsAsExpected")
				.given(
						cryptoCreate("payer")
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via("transferTxn")
				).then(finalClause);
	}

	private HapiApiSpec neitherSendAndReceiveThresholdsAreConsideredWhenNotCreating() {
		long A_LOW_THRESHOLD = 100L;

		return defaultHapiSpec("NeitherSendAndReceiveThresholdsAreConsideredWhenNotCreating")
				.given(
						cryptoCreate("lowSend").sendThreshold(A_LOW_THRESHOLD),
						cryptoCreate("lowReceive").receiveThreshold(A_LOW_THRESHOLD)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("lowSend", "lowReceive", A_LOW_THRESHOLD + 1L)
						).via("transferTxn")
				).then(
						getAccountRecords("lowSend").has(inOrder()),
						getAccountRecords("lowReceive").has(inOrder())
				);
	}

	/**
	 * Builds a spec in which we do a CryptoTransfer from an account with
	 * a low send threshold, to an account with a low receive threshold.
	 * Both should should get long-lived records of the transfer.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec bothSendAndReceiveThresholdsAreConsideredWhenCreating() {
		long A_LOW_THRESHOLD = 100L;

		return defaultHapiSpec("BothSendAndReceiveThresholdsAreConsidered")
				.given(
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.createThresholdRecords", "true"
						)),
						cryptoCreate("lowSend").sendThreshold(A_LOW_THRESHOLD),
						cryptoCreate("lowReceive").receiveThreshold(A_LOW_THRESHOLD)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("lowSend", "lowReceive", A_LOW_THRESHOLD + 1L)
						).via("transferTxn")
				).then(
						getAccountRecords("lowSend").has(inOrder(
								recordWith().txnId("transferTxn")
						)),
						getAccountRecords("lowReceive").has(inOrder(
								recordWith().txnId("transferTxn")
						)),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.createThresholdRecords", "false"
						))
				);
	}

	/**
	 * Builds a spec in which we create a new contract, then call it; both
	 * transactions should result in a record.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec unsuccessfullyCalledContractGetsRecord() {
		return defaultHapiSpec("UnsuccessfullyCalledContractGetsRecord")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn"),
						contractCall("contract", DEPOSIT_ABI, 1_000L).via("callTxn").sending(1L)
								.hasKnownStatus(CONTRACT_REVERT_EXECUTED)
				).then(
						getContractRecords("contract").has(inOrder(
								recordWith().txnId("createTxn"),
								recordWith().txnId("callTxn")
						))
				);
	}

	/**
	 * Builds a spec in which we create a new contract, then call it; both
	 * transactions should result in a record.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec successfullyCalledContractGetsRecord() {
		return defaultHapiSpec("SuccessfullyCalledContractGetsRecord")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn"),
						contractCall("contract", DEPOSIT_ABI, 1_000L).via("callTxn").sending(1_000L)
				).then(
						getContractRecords("contract").has(inOrder(
								recordWith().txnId("createTxn"),
								recordWith().txnId("callTxn")
						))
				);
	}

	/**
	 * Builds a spec in which we create a new contract, then call it; both
	 * receive a record of its creation.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec newlyCreatedContractGetsRecord() {
		return defaultHapiSpec("NewlyCreatedContractGetsRecord")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn")
				).then(
						getContractRecords("contract").has(inOrder(
								recordWith().txnId("createTxn")
						))
				);
	}

	/**
	 * Builds a spec in which we create new account with an initial
	 * balance greater than its receive threshold. The current policy
	 * is to NOT add record of the create transaction to the new account.
	 *
	 * @return the spec.
	 */
	private HapiApiSpec newlyCreatedAccountReceiveThresholdIsIgnored() {
		final long RECEIVE_THRESHOLD = 100L;

		return defaultHapiSpec("NewlyCreatedAccountReceiveThresholdIsIgnored")
				.given(
						cryptoCreate("lowReceiveThresh").receiveThreshold(RECEIVE_THRESHOLD)
				).when().then(
						getAccountRecords("lowReceiveThresh").has(inOrder())
				);
	}

	/**
	 * Creates a spec in which we pay for two CryptoTransfers using a payer
	 * with a very low send threshold and a very high receive threshold.
	 * 1. In the first CryptoTransfer, the payer is a net sender of funds; hence
	 * should get a long-lived record of the transfer.
	 * 2. In the second CryptoTransfer, the payer is a net receiver of funds,
	 * and should thus not get a long-lived record.
	 *
	 * In particular, the payer should have exactly one more record with the
	 * txnId of the first transfer than the txnId of the second transfer (no
	 * matter if short-lived records are being saved to state or not).
	 *
	 * @return the spec.
	 */
	private HapiApiSpec onlyNetAdjustmentIsComparedToThresholdWhenCreating() {
		final long WAY_LESS_THAN_A_TRANSFER_FEE = 1L;
		final long WAY_MORE_THAN_A_TRANSFER_FEE = 1_000_000_000L;

		return defaultHapiSpec("OnlyNetAdjustmentIsComparedToThresholdWhenCreating")
				.given(
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.createThresholdRecords", "true"
						)),
						cryptoCreate("lowSendThreshPayer")
								.sendThreshold(1L),
						cryptoCreate("misc")
								.balance(WAY_MORE_THAN_A_TRANSFER_FEE + WAY_LESS_THAN_A_TRANSFER_FEE)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("misc", "lowSendThreshPayer", WAY_LESS_THAN_A_TRANSFER_FEE)
						).payingWith("lowSendThreshPayer").via("thresholdRecordTxn"),
						cryptoTransfer(
								tinyBarsFromTo("misc", "lowSendThreshPayer", WAY_MORE_THAN_A_TRANSFER_FEE)
						).payingWith("lowSendThreshPayer").via("noThresholdRecordTxn")
				).then(
						assertionsHold((spec, assertLog) -> {
							HapiGetAccountRecords op = getAccountRecords("lowSendThreshPayer");
							allRunFor(spec, op);
							List<TransactionRecord> records =
									op.getResponse().getCryptoGetAccountRecords().getRecordsList();
							int numForThresholdRecordTxn = numRecordsWithTxnId(
									records,
									spec.registry().getTxnId("thresholdRecordTxn"));
							int numForNoThresholdRecordTxn = numRecordsWithTxnId(
									records,
									spec.registry().getTxnId("noThresholdRecordTxn"));
							assertEquals(
									"Wrong difference in records generated!",
									1,
									numForThresholdRecordTxn - numForNoThresholdRecordTxn);
						}),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"ledger.createThresholdRecords", "false"
						))
				);
	}

	private int numRecordsWithTxnId(List<TransactionRecord> records, TransactionID txnId) {
		return (int) records
				.stream()
				.filter(r -> r.getTransactionID().equals(txnId))
				.count();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";
	private final String DEPOSIT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}]," +
			"\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
}
