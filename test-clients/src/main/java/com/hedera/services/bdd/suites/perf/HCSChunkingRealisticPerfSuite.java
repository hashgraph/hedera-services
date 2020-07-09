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
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.chunkAFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class HCSChunkingRealisticPerfSuite extends LoadTest {

	private static final Logger log = LogManager.getLogger(HCSChunkingRealisticPerfSuite.class);
	private static final int CHUNK_SIZE = 5800;
	private static final String LARGE_FILE = "src/main/resource/RandomLargeBinary.bin";
	private static final String PAYER = "payer";
	private static final String TOPIC = "topic";

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(fragmentLongMessageIntoChunks());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private static HapiApiSpec fragmentLongMessageIntoChunks() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> submitBurst = () -> new HapiSpecOperation[] {
				chunkAFile(LARGE_FILE, CHUNK_SIZE, PAYER, TOPIC)
		};

		return defaultHapiSpec("fragmentLongMessageIntoChunks")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						newKeyNamed("submitKey"),
						logIt(ignore -> settings.toString())
				).when(
						cryptoCreate(PAYER).balance(initialBalance.getAsLong())
								.withRecharging()
								.rechargeWindow(30)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						createTopic(TOPIC)
								.submitKeyName("submitKey")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						sleepFor(5000) //wait all other thread ready
				).then(
						defaultLoadTest(submitBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
