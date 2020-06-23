package com.hedera.services.bdd.suites.consensus;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class ChunkingSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ChunkingSuite.class);

	public static void main(String... args) {
		new ChunkingSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				chunkNumberIsValidated()
		);
	}

	private HapiApiSpec chunkNumberIsValidated() {
		return defaultHapiSpec("messageSubmissionSimple")
				.given(
						createTopic("testTopic")
				)
				.when(
				)
				.then(
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(2, 3)
								.hasKnownStatus(INVALID_CHUNK_NUMBER)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
