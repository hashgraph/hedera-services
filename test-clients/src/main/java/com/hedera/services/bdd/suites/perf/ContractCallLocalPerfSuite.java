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

import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;

public class ContractCallLocalPerfSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallLocalPerfSuite.class);

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractCallLocalPerfSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				contractCallLocalPerf()
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

	HapiApiSpec contractCallLocalPerf() {
		final int NUM_CALLS = 1_000;

		return defaultHapiSpec("ContractCallLocalPerf")
				.given(
						fileCreate("bytecode").path(ContractResources.BALANCE_LOOKUP_BYTECODE_PATH),
						contractCreate("contract").bytecode("bytecode").balance(1_000L)
				).when(
						contractCallLocal(
								"contract",
								ContractResources.LOOKUP_ABI,
								spec -> new Object[] {
										spec.registry().getContractId("contract").getContractNum()
								}).recordNodePaymentAs("cost"),
						UtilVerbs.startThroughputObs("contractCallLocal")
				).then(
						UtilVerbs.inParallel(asOpArray(NUM_CALLS, ignore ->
										contractCallLocal(
												"contract",
												ContractResources.LOOKUP_ABI,
												spec -> new Object[] {
														spec.registry().getContractId("contract").getContractNum()
												}).nodePayment(spec -> spec.registry().getAmount("cost")))),
						UtilVerbs.finishThroughputObs("contractCallLocal")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
