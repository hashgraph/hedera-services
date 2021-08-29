package com.hedera.services.bdd.suites.contract;

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

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class OCTokenSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(OCTokenSpec.class);

	public static void main(String... args) {
		new OCTokenSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				messageSubmissionSizeChange(),
		});
	}

	private HapiApiSpec messageSubmissionSizeChange() {
		return defaultHapiSpec("messageSubmissionSizeChange")
				.given(
						newKeyNamed("submitKey"),
						createTopic("testTopic")
								.submitKeyName("submitKey")
				)
				.when(
						cryptoCreate("civilian"),
						submitMessageTo("testTopic")
								.message("testmessage")
								.payingWith("civilian")
								.hasRetryPrecheckFrom(BUSY)
								.hasKnownStatus(SUCCESS),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("consensus.message.maxBytesAllowed", "20"))
				)
				.then(
						submitMessageTo("testTopic")
								.message("testmessagetestmessagetestmessagetestmessage")
								.payingWith("civilian")
								.hasRetryPrecheckFrom(BUSY)
								.hasKnownStatus(MESSAGE_SIZE_TOO_LARGE),
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("consensus.message.maxBytesAllowed", "1024"))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
