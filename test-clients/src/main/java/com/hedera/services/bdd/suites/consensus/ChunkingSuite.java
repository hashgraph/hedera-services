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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
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
				chunkNumberIsValidated(),
				chunkTransactionIDIsValidated()
		);
	}

	private HapiApiSpec chunkNumberIsValidated() {
		return defaultHapiSpec("chunkNumberIsValidated")
				.given(
						createTopic("testTopic")
				)
				.when(
				)
				.then(
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(2, 3)
								.hasKnownStatus(INVALID_CHUNK_NUMBER),
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(3, 2)
								.hasKnownStatus(SUCCESS),
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(5, 5)
								.hasKnownStatus(SUCCESS)

				);
	}

	private HapiApiSpec chunkTransactionIDIsValidated() {
		return defaultHapiSpec("chunkTransactionIDIsValidated")
				.given(
						cryptoCreate("initialTransactionPayer"),
						createTopic("testTopic")
				)
				.when(
				)
				.then(
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(3, 2, "initialTransactionPayer")
								.hasKnownStatus(INVALID_CHUNK_TRANSACTION_ID),
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(3, 3, "initialTransactionPayer")
								.payingWith("initialTransactionPayer")
								// Add delay to make sure the valid start of the transaction will not match
								// that of the initialTransactionID
								.delayBy(1000)
								.hasKnownStatus(SUCCESS),
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(2, 1)
								// Also add delay here
								.delayBy(1000)
								.hasKnownStatus(INVALID_CHUNK_TRANSACTION_ID),
						submitMessageTo("testTopic")
								.message("testmessage")
								.chunkInfo(4, 1)
								.via("firstChunk")
								.payingWith("initialTransactionPayer")
								.usePresetTimestamp()
								.hasKnownStatus(SUCCESS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
