package com.hedera.services.bdd.suites.freeze;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.suites.freeze.UpdateServerFiles.generateUpdateData;

// Only send the freeze transaction with update file info

public class FreezeUpdateOnly extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeUpdateOnly.class);
	private static String fileIDString = "UPDATE_FEATURE"; // mnemonic for file 0.0.150
	public static void main(String... args) {
		new FreezeUpdateOnly().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				postiveTests()
		);
	}

	private List<HapiApiSpec> postiveTests() {
		return Arrays.asList(
				FreezeUpdateOnly()
		);
	}

	private HapiApiSpec FreezeUpdateOnly() {
		final byte [] data = generateUpdateData(true);

		final byte[] hash = UpdateFile150.sha384Digest(data);
		return defaultHapiSpec("FreezeUpdateOnly")
				.given(
						freeze().setFileID(fileIDString)
								.setFileHash(hash).payingWith(GENESIS)
								.startingIn(60).seconds().andLasting(10).minutes()
				).when(

				).then(
				);
	}
}
