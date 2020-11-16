package com.hedera.services.bdd.suites.contract;

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
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.isLiteralResult;
import static com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts.resultWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static java.util.concurrent.TimeUnit.MINUTES;

public class PerpetualCalls extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PerpetualCalls.class);

	final String PATH_TO_TARGET_BYTECODE = "src/main/resource/testfiles/ExtMultiPurpose.bin";

	private static final String GET_TRADITIONAL_VALUE_ABI = "{\"constant\":true,\"inputs\":[]," +
			"\"name\":\"answerTraditionally\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}]," +
			"\"payable\":false,\"stateMutability\":\"pure\",\"type\":\"function\"}";
	private static final String LUCKY_NO_LOOKUP_ABI = "{\"constant\":true,\"inputs\":[],\"name\":\"pick\"," +
			"\"outputs\":[{\"internalType\":\"uint32\",\"name\":\"\",\"type\":\"uint32\"}],\"payable\":false," +
			"\"stateMutability\":\"view\",\"type\":\"function\"}";

	private AtomicLong duration = new AtomicLong(Long.MAX_VALUE);
	private AtomicReference<TimeUnit> unit = new AtomicReference<>(MINUTES);
	private AtomicInteger maxOpsPerSec = new AtomicInteger(10);

	public static void main(String... args) {
		new PerpetualCalls().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						callsForever(),
				}
		);
	}

	private HapiApiSpec callsForever() {
		return defaultHapiSpec("CallsForever").
				given().when().then(
				runWithProvider(callsFactory())
						.lasting(duration::get, unit::get)
						.maxOpsPerSec(maxOpsPerSec::get)
		);
	}

	private Function<HapiApiSpec, OpProvider> callsFactory() {
		return spec -> new OpProvider() {
			@Override
			public List<HapiSpecOperation> suggestedInitializers() {
				return List.of(
						fileCreate("bytecode").path(PATH_TO_TARGET_BYTECODE),
						contractCreate("extMulti").bytecode("bytecode")
				);
			}

			@Override
			public Optional<HapiSpecOperation> get() {
//				var op = contractCall("extMulti", GET_TRADITIONAL_VALUE_ABI).deferStatusResolution();
				var op = contractCall("extMulti", LUCKY_NO_LOOKUP_ABI).deferStatusResolution();
				return Optional.of(op);
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
