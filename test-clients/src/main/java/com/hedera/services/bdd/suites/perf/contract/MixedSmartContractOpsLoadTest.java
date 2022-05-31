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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType.THRESHOLD;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

/**
 * Run mixed operations including ContractCreate, ContractUpdate, ContractCallLocal, ContractCall, ContractInfo
 */
public class MixedSmartContractOpsLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(MixedSmartContractOpsLoadTest.class);

	public static void main(String... args) {
		parseArgs(args);

		MixedSmartContractOpsLoadTest suite = new MixedSmartContractOpsLoadTest();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				RunMixedSmartContractOps()
		);
	}

	protected HapiApiSpec RunMixedSmartContractOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger createdSoFar = new AtomicInteger(0);
		final String SOME_BYTE_CODE = "contractByteCode";
		final String UPDATABLE_CONTRACT = "updatableContract";
		final String CONTRACT_NAME_PREFIX = "testContract";
		final String PAYABLE_CONTRACT = "PayReceivable";
		final String LOOKUP_CONTRACT = "BalanceLookup";
		final String CIVILIAN_ACCOUNT = "civilian";
		final int depositAmount = 1;

		Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
				/* create a contract */
				contractCreate(CONTRACT_NAME_PREFIX + createdSoFar.getAndIncrement())
						.bytecode(SOME_BYTE_CODE)
						.hasAnyPrecheck()
						.deferStatusResolution(),

				/* update the memo and  do get info on the contract that needs to be updated */
				contractUpdate(UPDATABLE_CONTRACT)
						.newMemo(new String(randomUtf8Bytes(memoLength.getAsInt())))
						.hasAnyPrecheck()
						.deferStatusResolution(),

				/* call balance lookup contract and contract to deposit funds*/
				contractCallLocal(LOOKUP_CONTRACT,
						"lookup",
						spec -> new Object[] { spec.registry().getAccountID(CIVILIAN_ACCOUNT).getAccountNum() }
				).payingWith(GENESIS),

				contractCall(PAYABLE_CONTRACT, "deposit", depositAmount)
						.sending(depositAmount)
						.suppressStats(true)
						.deferStatusResolution()
		};
		return defaultHapiSpec("RunMixedSmartContractOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				)
				.when(
						/* create an account */
						cryptoCreate(CIVILIAN_ACCOUNT).balance(ONE_HUNDRED_HBARS),

						/* create a file with some contents and contract with it */
						fileCreate(SOME_BYTE_CODE).path(HapiSpecSetup.getDefaultInstance().defaultContractPath()),
						contractCreate(UPDATABLE_CONTRACT).bytecode(SOME_BYTE_CODE).adminKey(THRESHOLD),

						/* create a contract which does a query to look up balance of the civilan account */
						uploadInitCode(LOOKUP_CONTRACT),
						contractCreate(LOOKUP_CONTRACT).adminKey(THRESHOLD),

						/* create a contract that does a transaction to deposit funds */
						fileCreate(PAYABLE_CONTRACT),
						contractCreate(PAYABLE_CONTRACT).adminKey(THRESHOLD),

						/* get contract info on all contracts created */
						getContractInfo(LOOKUP_CONTRACT).hasExpectedInfo().logged(),
						getContractInfo(PAYABLE_CONTRACT).hasExpectedInfo().logged(),
						getContractInfo(UPDATABLE_CONTRACT).hasExpectedInfo().logged()
				)
				.then(
						defaultLoadTest(mixedOpsBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
