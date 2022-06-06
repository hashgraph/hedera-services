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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.finishThroughputObs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.startThroughputObs;

public class SubmitMessagePerfSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SubmitMessagePerfSuite.class);

	public static void main(String... args) {
		SubmitMessagePerfSuite suite = new SubmitMessagePerfSuite();
		suite.runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return Arrays.asList(submitMessagePerf());
	}

	@Override
	public boolean canRunConcurrent() {
		return false;
	}

	private HapiApiSpec submitMessagePerf() {
		final int NUM_SUBMISSIONS = 100_000;

		return defaultHapiSpec("submitMessagePerf")
				.given(
						newKeyNamed("submitKey"),
						createTopic("testTopic")
								.submitKeyName("submitKey")
				).when(
						startThroughputObs("submitMessageThroughput")
								.msToSaturateQueues(50L),
						inParallel(
								// only ask for record for the last transaction
								asOpArray(NUM_SUBMISSIONS, i ->
										(i == (NUM_SUBMISSIONS - 1)) ?
												submitMessageTo("testTopic")
														.message("testMessage" + i) :
												submitMessageTo("testTopic")
														.message("testMessage" + i)
														.deferStatusResolution()
								)
						)
				).then(
						finishThroughputObs("submitMessageThroughput").gatedByQuery(() ->
								getTopicInfo("testTopic").hasSeqNo(NUM_SUBMISSIONS).logged()
						).sleepMs(1_000L).expiryMs(300_000L)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

