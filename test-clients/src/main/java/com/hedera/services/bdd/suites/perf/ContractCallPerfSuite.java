package com.hedera.services.bdd.suites.perf;

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

import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;

import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;

public class ContractCallPerfSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallPerfSuite.class);

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractCallPerfSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				contractCallPerf()
		);
	}

	@Override
	public boolean leaksState() {
		return true;
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	HapiApiSpec contractCallPerf() {
		final int NUM_CALLS = 1_000;
		final long ENDING_BALANCE = NUM_CALLS * (NUM_CALLS + 1)	 / 2;
		final String DEPOSIT_MEMO = "So we out-danced thought, body perfection brought...";

		return defaultHapiSpec("ContractCallPerf")
				.given(
						fileCreate("contractBytecode").path(ContractResources.VERBOSE_DEPOSIT_BYTECODE_PATH),
						contractCreate("perf").bytecode("contractBytecode"),
						fileCreate("lookupBytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
						contractCreate("balanceLookup").bytecode("lookupBytecode").balance(1L)
				).when(
						getContractInfo("perf").hasExpectedInfo().logged(),
						UtilVerbs.startThroughputObs("contractCall").msToSaturateQueues(50L)
				).then(
						UtilVerbs.inParallel(asOpArray(NUM_CALLS, i ->
								contractCall("perf", ContractResources.VERBOSE_DEPOSIT_ABI, i + 1, 0, DEPOSIT_MEMO)
										.sending(i + 1)
										.deferStatusResolution())),
						UtilVerbs.finishThroughputObs("contractCall")
								.gatedByQuery(() ->
										contractCallLocal(
												"balanceLookup",
												ContractResources.LOOKUP_ABI,
												spec -> new Object[] {
													spec.registry().getContractId("perf").getContractNum()
												}
										).has(
												resultWith().resultThruAbi(
														ContractResources.LOOKUP_ABI,
														isLiteralResult(
																new Object[] { BigInteger.valueOf(ENDING_BALANCE) }
														)
												)
										).noLogging()
								)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
