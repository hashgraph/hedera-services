package com.hedera.services.bdd.suites.crypto;

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

import java.util.List;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getBySolidityIdNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getClaimNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getFastRecordNotSupported;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.getStakersNotSupported;

public class UnsupportedQueriesRegression extends HapiApiSuite {
	static final Logger log = LogManager.getLogger(UnsupportedQueriesRegression.class);

	public static void main(String... args) {
		new UnsupportedQueriesRegression().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						verifyUnsupportedOps(),
				}
		);
	}

	private HapiApiSpec verifyUnsupportedOps() {
		return defaultHapiSpec("VerifyUnsupportedOps")
				.given( ).when( ).then(
						getClaimNotSupported(),
						getStakersNotSupported(),
						getFastRecordNotSupported(),
						getBySolidityIdNotSupported()
				);
	}
}
