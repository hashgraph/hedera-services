package com.hedera.services.bdd.suites.perf.contract;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;

public class ContractCallLocalPerfSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractCallLocalPerfSuite.class);

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ContractCallLocalPerfSuite().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				contractCallLocalPerf()
		);
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	HapiApiSpec contractCallLocalPerf() {
		final int NUM_CALLS = 1_000;
		final var contract = "BalanceLookup";

		return defaultHapiSpec("ContractCallLocalPerf")
				.given(
						uploadInitCode(contract),
						contractCreate(contract).balance(1_000L)
				).when(
						contractCallLocal(
								contract,
								"lookup",
								spec -> new Object[]{
										spec.registry().getContractId(contract).getContractNum()
								}).recordNodePaymentAs("cost"),
						UtilVerbs.startThroughputObs("contractCallLocal")
				).then(
						UtilVerbs.inParallel(asOpArray(NUM_CALLS, ignore ->
								contractCallLocal(
										contract,
										"lookup",
										spec -> new Object[]{
												spec.registry().getContractId(contract).getContractNum()
										}).nodePayment(spec -> spec.registry().getAmount("cost")))),
						UtilVerbs.finishThroughputObs("contractCallLocal")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
