package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.HapiSpecSetup;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.inOrder;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.assertions.TransferListAsserts.atLeastOneTransfer;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;

public class ContractGetRecordsSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractGetRecordsSuite.class);

	public static void main(String... args) {
		new ContractGetRecordsSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
			positiveSpecs(),
			negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
			insufficientFee(),
			invalidContract()
		);
	}

	/* NOTE: the `recordsExpire` spec requires a ledger configured with
	record TTL to be the same as the effective `record.ttl.ms` property. */
	List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(
//				recordsExpire(),
				vanillaSuccess()
		);
	}

	HapiApiSpec vanillaSuccess() {
		return defaultHapiSpec("VanillaSuccess")
				.given(
						contractCreate("testContract").adminKey(THRESHOLD)
				).when().then(
					QueryVerbs.getContractRecords("testContract").has(
							inOrder(
									recordWith().transfers(atLeastOneTransfer()).contractCreateResult(
											resultWith().contract("testContract")))));
	}

	HapiApiSpec recordsExpire() {
		long timeExceedingRecordTtl = HapiSpecSetup.getDefaultInstance().recordTtlMs() + 5_000L;

		return defaultHapiSpec("RecordsExpire")
				.given(
						contractCreate("testContract"),
						QueryVerbs.getContractRecords("testContract").has(inOrder(recordWith()))
				).when(
						sleepFor(timeExceedingRecordTtl)
				).then(
						QueryVerbs.getContractRecords("testContract").has(inOrder(recordWith())),
						QueryVerbs.getContractRecords("testContract").has(inOrder()));
	}

	HapiApiSpec insufficientFee() {
		return defaultHapiSpec("InsufficientFee")
				.given(
						contractCreate("testContract")
				).when().then(
						QueryVerbs.getContractRecords("testContract")
								.nodePayment(0L)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE));
	}

	HapiApiSpec invalidContract() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						QueryVerbs.getContractRecords(invalidContract)
								.hasCostAnswerPrecheck(INVALID_CONTRACT_ID));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
