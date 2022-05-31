package com.hedera.services.bdd.suites.perf.topic;


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
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.chunkAFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;

public class HCSChunkingRealisticPerfSuite extends LoadTest {

	private static final Logger log = LogManager.getLogger(HCSChunkingRealisticPerfSuite.class);
	private static final int CHUNK_SIZE = 150;
	private static final String LARGE_FILE = "src/main/resource/contract/contracts/EmitEvent/EmitEvent.bin";
	private static final String PAYER = "payer";
	private static final String TOPIC = "topic";
	public static final int DEFAULT_COLLISION_AVOIDANCE_FACTOR = 2;
	private static AtomicLong totalMsgSubmitted = new AtomicLong(0);

	public static void main(String... args) {
		HCSChunkingRealisticPerfSuite suite = new HCSChunkingRealisticPerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(fragmentLongMessageIntoChunks());
	}

	private static HapiApiSpec fragmentLongMessageIntoChunks() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> submitBurst = () -> new HapiSpecOperation[] {
				chunkAFile(LARGE_FILE, CHUNK_SIZE, PAYER, TOPIC, totalMsgSubmitted)
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
						withOpContext((spec, ignore) -> {
							int factor = settings.getIntProperty("collisionAvoidanceFactor",
									DEFAULT_COLLISION_AVOIDANCE_FACTOR);
							List<HapiSpecOperation> opsList = new ArrayList<HapiSpecOperation>();
							for (int i = 0; i < settings.getThreads() * factor; i++) {
								var op = createTopic(TOPIC + i)
										.submitKeyName("submitKey")
										.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION,
												PLATFORM_TRANSACTION_NOT_CREATED);
								opsList.add(op);
							}
							CustomSpecAssert.allRunFor(spec, inParallel(flattened(opsList)));
						}),
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
