package com.hedera.services.bdd.suites.perf.file;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

/**
 * Run FileCreate, FileAppend, FileUpdate operations in burst while the test is running
 */
public class MixedFileOpsLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(MixedFileOpsLoadTest.class);
	private final String FILE_NAME_PREFIX = "testFile";
	final AtomicInteger createdSoFar = new AtomicInteger(0);

	public static void main(String... args) {
		parseArgs(args);

		MixedFileOpsLoadTest suite = new MixedFileOpsLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				RunMixedFileOps()
		);
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	protected HapiApiSpec RunMixedFileOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		int fileSize = TxnUtils.BYTES_4K;
		final byte[] initialContent = randomUtf8Bytes(fileSize);
		final byte[] memo = randomUtf8Bytes(memoLength.getAsInt());
		final byte[] newContents = TxnUtils.randomUtf8Bytes(fileSize);

		Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> new HapiSpecOperation[] {
				fileCreate(FILE_NAME_PREFIX + createdSoFar.getAndIncrement())
						.entityMemo(new String(memo))
						.logging()
						.contents(initialContent)
						.hasAnyPrecheck()
						.deferStatusResolution(),
				getFileInfo(FILE_NAME_PREFIX + createdSoFar.get()).logging(),
				fileUpdate(FILE_NAME_PREFIX + createdSoFar.get())
						.contents(newContents)
						.logging()
						.deferStatusResolution(),
				fileAppend(FILE_NAME_PREFIX + createdSoFar.get())
						.content("dummy")
						.logging()
						.deferStatusResolution()
		};
		return defaultHapiSpec("RunMixedFileOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				)
				.when(
						fileCreate(FILE_NAME_PREFIX + createdSoFar.getAndIncrement())
								.entityMemo(new String(memo))
								.logging()
								.contents("initial content")
								.hasPrecheckFrom(standardPermissiblePrechecks)
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
