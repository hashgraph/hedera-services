package com.hedera.services.bdd.suites.throttling;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.ThrottleDefsLoader.protoDefsFromResource;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;

public class GasLimitThrottlingSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(GasLimitThrottlingSuite.class);
	private static final byte[] totalLimits =
			protoDefsFromResource("testSystemFiles/throttles-gas-limit-1M.json").toByteArray();

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						txsUnderGasLimitAllowed(),
						txOverGasLimitThrottled()
				}
		);
	}

	public static void main(String... args) {
		new GasLimitThrottlingSuite().runSuiteSync();
	}

	private HapiApiSpec txsUnderGasLimitAllowed() {
		final int NUM_CALLS = 10;
		return defaultHapiSpec("TXsUnderGasLimitAllowed")
				.given(
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "true"),
						UtilVerbs.overriding("contracts.maxGas", "10000000"),
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatusFrom(SUCCESS, SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).when(
						/* we need the payer account, see SystemPrecheck IS_THROTTLE_EXEMPT */
						cryptoCreate("payerAccount").balance(ONE_MILLION_HBARS),
						fileCreate("contractBytecode").path(ContractResources.BENCHMARK_CONTRACT),
						contractCreate("perf").bytecode("contractBytecode").payingWith("payerAccount")
				).then(
						UtilVerbs.inParallel(
								asOpArray(NUM_CALLS, i ->
										contractCall(
												"perf", ContractResources.TWO_SSTORES,
												Bytes.fromHexString("0x05").toArray()
										)
												.gas(100_000)
												.payingWith("payerAccount")
												.hasKnownStatusFrom(SUCCESS, OK)
								)
						),
						UtilVerbs.sleepFor(1000),
						contractCall("perf", ContractResources.TWO_SSTORES,
								Bytes.fromHexString("0x06").toArray())
								.gas(1_000_000L)
								.payingWith("payerAccount")
								.hasKnownStatusFrom(SUCCESS, OK)
				);
	}

	private HapiApiSpec txOverGasLimitThrottled() {
		return defaultHapiSpec("TXOverGasLimitThrottled")
				.given(
						UtilVerbs.overriding("contracts.throttle.throttleByGas", "true"),
						fileUpdate(THROTTLE_DEFS)
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(EXCHANGE_RATE_CONTROL)
								.contents(totalLimits)
								.hasKnownStatusFrom(SUCCESS, SUCCESS_BUT_MISSING_EXPECTED_OPERATION)
				).when(
						cryptoCreate("payerAccount").balance(ONE_MILLION_HBARS),
						fileCreate("contractBytecode").path(ContractResources.BENCHMARK_CONTRACT),
						contractCreate("perf").bytecode("contractBytecode")
				).then(
						contractCall(
								"perf", ContractResources.TWO_SSTORES,
								Bytes.fromHexString("0x05").toArray()
						)
								.gas(1_000_001)
								.payingWith("payerAccount")
								.hasPrecheck(BUSY)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
