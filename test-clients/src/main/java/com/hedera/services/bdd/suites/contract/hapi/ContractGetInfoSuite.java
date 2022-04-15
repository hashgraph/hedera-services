package com.hedera.services.bdd.suites.contract.hapi;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractInfoAsserts.contractWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class ContractGetInfoSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractGetInfoSuite.class);

	private static final String NON_EXISTING_CONTRACT = HapiSpecSetup.getDefaultInstance().invalidContractName();

	public static void main(String... args) {
		new ContractGetInfoSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				getInfoWorks(),
				invalidContractFromCostAnswer(),
				invalidContractFromAnswerOnly()
		);
	}

	private HapiApiSpec getInfoWorks() {
		final var contract = "Multipurpose";
		final var MEMO = "This is a test.";
		return defaultHapiSpec("GetInfoWorks")
				.given(
						newKeyNamed("adminKey"),
						uploadInitCode(contract),
						contractCreate(contract)
								.adminKey("adminKey")
								.entityMemo(MEMO)
								.autoRenewSecs(6999999L)
				).when().then(
						getContractInfo(contract)
								.hasExpectedLedgerId("0x03")
								.hasExpectedInfo()
								.has(
										contractWith()
												.memo(MEMO)
												.adminKey("adminKey")
								)
				);
	}

	private HapiApiSpec invalidContractFromCostAnswer() {
		return defaultHapiSpec("InvalidContractFromCostAnswer")
				.given().when().then(
						getContractInfo(NON_EXISTING_CONTRACT)
								.hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}

	private HapiApiSpec invalidContractFromAnswerOnly() {
		return defaultHapiSpec("InvalidContractFromAnswerOnly")
				.given().when().then(
						getContractInfo(NON_EXISTING_CONTRACT)
								.nodePayment(27_159_182L)
								.hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
