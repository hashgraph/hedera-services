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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;

public class DeleteFailuresSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(DeleteFailuresSpec.class);

	public static void main(String... args) {
		new DeleteFailuresSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						handleRejectsMissingFile(),
						handleRejectsDeletedFile(),
				}
		);
	}

	private HapiApiSpec handleRejectsMissingFile() {
		return defaultHapiSpec("handleRejectsMissingFile")
				.given(
				).when( ).then(
						fileDelete("1.2.3")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.INVALID_FILE_ID)
				);
	}

	private HapiApiSpec handleRejectsDeletedFile() {
		return defaultHapiSpec("handleRejectsDeletedFile")
				.given(
						fileCreate("tbd")
				).when(
						fileDelete("tbd")
				).then(
						fileDelete("tbd")
								.hasKnownStatus(ResponseCodeEnum.FILE_DELETED)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
