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
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.util.concurrent.TimeUnit.MINUTES;

public class FileUpdateLoadTest extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FileUpdateLoadTest.class);

	public static void main(String... args) {
		FileUpdateLoadTest suite = new FileUpdateLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runFileUpdates());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private HapiApiSpec runFileUpdates() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger submittedSoFar = new AtomicInteger(0);
		final byte[] NEW_CONTENTS = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);

		Supplier<HapiSpecOperation[]> fileUpdateBurst = () -> new HapiSpecOperation[] {
				inParallel(IntStream.range(0, settings.getBurstSize())
						.mapToObj(i ->
								TxnVerbs.fileUpdate("target")
										.fee(Integer.MAX_VALUE)
										.contents(NEW_CONTENTS)
										.noLogging()
										.hasPrecheckFrom(
												OK,
												BUSY,
												DUPLICATE_TRANSACTION,
												PLATFORM_TRANSACTION_NOT_CREATED)
										.deferStatusResolution())
						.toArray(n -> new HapiSpecOperation[n])),
				logIt(ignore ->
						String.format(
								"Now a total of %d file updates submitted.",
								submittedSoFar.addAndGet(settings.getBurstSize()))),
		};

		return defaultHapiSpec("RunFileUpdates")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
						fileCreate("target").contents("The initial contents!")
				).then(
						runLoadTest(fileUpdateBurst)
								.tps(settings::getTps)
								.tolerance(settings::getTolerancePercentage)
								.allowedSecsBelow(settings::getAllowedSecsBelow)
								.lasting(settings::getMins, () -> MINUTES)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
