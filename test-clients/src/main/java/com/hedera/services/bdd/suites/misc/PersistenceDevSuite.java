package com.hedera.services.bdd.suites.misc;

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

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;

public class PersistenceDevSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(PersistenceDevSuite.class);


	public static void main(String... args) throws Exception {
		new PersistenceDevSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						testEntityLoading(),
				}
		);
	}

	private HapiApiSpec testEntityLoading() {
		return customHapiSpec("TestEntityLoading").withProperties(Map.of(
				"persistentEntities.dir.path", "persistent-entities/"
		)).given(
				getTokenInfo("knownToken").logged()
		).when(
		).then(
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
