package com.hedera.services.bdd.suites.file.negative;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FILE_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_FILE_SIZE_EXCEEDED;

public class QueryFailuresSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(QueryFailuresSpec.class);

	public static void main(String... args) {
		new QueryFailuresSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						getsExpectedRejections(),
				}
		);
	}

	private HapiApiSpec getsExpectedRejections() {
		return defaultHapiSpec("getsExpectedRejections")
				.given(
						fileCreate("tbd"),
						fileDelete("tbd")
				).when().then(
						getFileInfo("1.2.3")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(INVALID_FILE_ID),
						getFileContents("1.2.3")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(INVALID_FILE_ID),
						getFileContents("tbd")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(FILE_DELETED)
								.logged(),
						getFileInfo("tbd")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(FILE_DELETED)
								.logged()
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
