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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.persistence.Account;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertionsHold;
import static org.junit.Assert.assertEquals;

public class RecordCreationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RecordCreationSuite.class);

	public static void main(String... args) {
		new RecordCreationSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						payerRecordCreationSanityChecks(),
						newlyCreatedContractNoLongerGetsRecord(),
						accountsGetPayerRecordsIfSoConfigured(),
						calledContractNoLongerGetsRecord(),
						thresholdRecordsDontExistAnymore(),
				}
		);
	}

	private HapiApiSpec payerRecordCreationSanityChecks() {
		return defaultHapiSpec("PayerRecordCreationSanityChecks")
				.given(
						cryptoCreate("payer")
				).when(
						createTopic("ofGeneralInterest").payingWith("payer"),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer"),
						submitMessageTo("ofGeneralInterest")
								.message("I say!")
								.payingWith("payer")
				).then(
						assertionsHold((spec, opLog) -> {
							final var payerId = spec.registry().getAccountID("payer");
							final var subOp = getAccountRecords("payer").logged();
							allRunFor(spec, subOp);
							final var records = subOp.getResponse().getCryptoGetAccountRecords().getRecordsList();
							assertEquals(3, records.size());
							for (var record : records) {
								assertEquals(record.getTransactionFee(), -netChangeIn(record, payerId));
							}
						})
				);
	}

	private long netChangeIn(TransactionRecord record, AccountID id) {
		return record.getTransferList().getAccountAmountsList().stream()
				.filter(aa -> id.equals(aa.getAccountID()))
				.mapToLong(AccountAmount::getAmount)
				.sum();
	}

	private HapiApiSpec accountsGetPayerRecordsIfSoConfigured() {
		return defaultHapiSpec("AccountsGetPayerRecordsIfSoConfigured")
				.given(
						cryptoCreate("payer"),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "false"))
				).when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "false")),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via("firstXfer"),
						getAccountRecords("payer").has(inOrder()),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "true"))
				).then(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via("secondXfer"),
						getAccountRecords("payer").has(inOrder(recordWith().txnId("secondXfer")))
				);
	}

	private HapiApiSpec calledContractNoLongerGetsRecord() {
		return defaultHapiSpec("CalledContractNoLongerGetsRecord")
				.given(
						fileCreate("bytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn"),
						contractCall("contract", ContractResources.DEPOSIT_ABI, 1_000L).via("callTxn").sending(1_000L)
				).then(
						getContractRecords("contract").has(inOrder())
				);
	}

	private HapiApiSpec newlyCreatedContractNoLongerGetsRecord() {
		return defaultHapiSpec("NewlyCreatedContractNoLongerGetsRecord")
				.given(
						fileCreate("bytecode").path(ContractResources.PAYABLE_CONTRACT_BYTECODE_PATH)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn")
				).then(
						getContractRecords("contract").has(inOrder())
				);
	}

	private HapiApiSpec thresholdRecordsDontExistAnymore() {
		return defaultHapiSpec("OnlyNetAdjustmentIsComparedToThresholdWhenCreating")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("lowSendThreshold").sendThreshold(1L),
						cryptoCreate("lowReceiveThreshold").receiveThreshold(1L),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"ledger.keepRecordsInState", "true"
								))
				).when(
						cryptoTransfer(
								tinyBarsFromTo(
										"lowSendThreshold",
										"lowReceiveThreshold",
										2L)
						).payingWith("payer").via("testTxn")
				).then(
						getAccountRecords("payer").has(inOrder(recordWith().txnId("testTxn"))),
						getAccountRecords("lowSendThreshold").has(inOrder()),
						getAccountRecords("lowReceiveThreshold").has(inOrder()),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"ledger.keepRecordsInState", "false"
								))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
