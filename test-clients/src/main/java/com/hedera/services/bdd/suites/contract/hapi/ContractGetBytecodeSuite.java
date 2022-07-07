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

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Assertions;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractBytecode;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.contract.Utils.getResourcePath;

public class ContractGetBytecodeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractGetBytecodeSuite.class);
	private static final String NON_EXISTING_CONTRACT = HapiSpecSetup.getDefaultInstance().invalidContractName();

	public static void main(String... args) {
		new ContractGetBytecodeSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				getByteCodeWorks(),
				invalidContractFromCostAnswer(),
				invalidContractFromAnswerOnly()
		);
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	private HapiApiSpec getByteCodeWorks() {
		final var contract = "EmptyConstructor";
		return HapiApiSpec.defaultHapiSpec("GetByteCodeWorks")
				.given(
						uploadInitCode(contract),
						contractCreate(contract)
				).when(
				).then(
						withOpContext((spec, opLog) -> {
							final var getBytecode = getContractBytecode(contract).saveResultTo(
									"contractByteCode");
							allRunFor(spec, getBytecode);

							@SuppressWarnings("UnstableApiUsage")
							final var originalBytecode = Hex.decode(Files.toByteArray(new File(getResourcePath(contract, ".bin"))));
							final var actualBytecode = spec.registry().getBytes("contractByteCode");
							// The original bytecode is modified on deployment
							final var expectedBytecode = Arrays.copyOfRange(originalBytecode, 29,
									originalBytecode.length);
							Assertions.assertArrayEquals(expectedBytecode, actualBytecode);
						})
				);
	}

	private HapiApiSpec invalidContractFromCostAnswer() {
		return defaultHapiSpec("InvalidContractFromCostAnswer")
				.given().when().then(
						getContractBytecode(NON_EXISTING_CONTRACT)
							.hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}

	private HapiApiSpec invalidContractFromAnswerOnly() {
		return defaultHapiSpec("InvalidContractFromAnswerOnly")
				.given().when().then(
						getContractBytecode(NON_EXISTING_CONTRACT)
								.nodePayment(27_159_182L)
								.hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}
}
