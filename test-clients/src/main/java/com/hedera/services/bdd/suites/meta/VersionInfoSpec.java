package com.hedera.services.bdd.suites.meta;

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

import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getVersionInfo;

import java.util.List;

public class VersionInfoSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(VersionInfoSpec.class);

	public static void main(String... args) {
		new VersionInfoSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						discoversExpectedVersions()
				}
		);
	}

	private HapiApiSpec discoversExpectedVersions() {
		return defaultHapiSpec("getsExpectedVersions").given().when().then(
				getVersionInfo()
						.logged()
						.hasNoDegenerateSemvers()
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
