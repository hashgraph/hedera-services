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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.concurrent.TimeUnit.MINUTES;

public class ContractCallLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(ContractCallLoadTest.class);
	final String PATH_TO_VERBOSE_CONTRACT_BYTECODE = "src/main/resource/testfiles/VerboseDeposit.bin";
	final String ABI = "{\"constant\":false,\"inputs\":[{\"internalType\":\"uint32\",\"name\":\"amount\"," +
			"\"type\":\"uint32\"},{\"internalType\":\"uint32\",\"name\":\"timesForEmphasis\",\"type\":\"uint32\"}," +
			"{\"internalType\":\"string\",\"name\":\"memo\",\"type\":\"string\"}],\"name\":\"deposit\"," +
			"\"outputs\":[]," +
			"\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";

	final String PATH_TO_LOOKUP_BYTECODE = "src/main/resource/testfiles/BalanceLookup.bin";


	public static void main(String... args) {
		parseArgs(args);

		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		ContractCallLoadTest suite = new ContractCallLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runContractCalls());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runContractCalls() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger submittedSoFar = new AtomicInteger(0);
		final String DEPOSIT_MEMO = "So we out-danced thought, body perfection brought...";

		Supplier<HapiSpecOperation[]> callBurst = () -> new HapiSpecOperation[] {
				inParallel(IntStream.range(0, settings.getBurstSize())
						.mapToObj(i ->
								contractCall("perf", ABI, i + 1, 0, DEPOSIT_MEMO)
										.sending(i + 1)
										.noLogging()
										.suppressStats(true)
										.deferStatusResolution())
						.toArray(n -> new HapiSpecOperation[n])),
				logIt(ignore ->
						String.format(
								"Now a total of %d transactions submitted.",
								submittedSoFar.addAndGet(settings.getBurstSize()))),
		};

		return defaultHapiSpec("runContractCalls")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						fileCreate("contractBytecode").path(PATH_TO_VERBOSE_CONTRACT_BYTECODE),
						contractCreate("perf").bytecode("contractBytecode"),
						fileCreate("lookupBytecode").path(PATH_TO_LOOKUP_BYTECODE),
						contractCreate("balanceLookup").bytecode("lookupBytecode").balance(1L),
						getContractInfo("perf").hasExpectedInfo().logged()

				).then(
						defaultLoadTest(callBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}


