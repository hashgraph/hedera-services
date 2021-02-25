package com.hedera.services.bdd.suites.regression;

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
import com.hedera.services.bdd.spec.assertions.AccountInfoAsserts;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.checkPersistentEntities;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

public class AddWellKnownEntities extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(AddWellKnownEntities.class);

	public static void main(String... args) {
		var hero = new AddWellKnownEntities();

		hero.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						instantiateEntities(),
				}
		);
	}

	private HapiApiSpec instantiateEntities() {
		return HapiApiSpec.customHapiSpec("AddWellKnownEntities")
				.withProperties(Map.of(
						"fees.useFixedOffer", "true",
						"fees.fixedOffer", "" + A_HUNDRED_HBARS,
						"persistentEntities.dir.path", "src/main/resource/jrs-creations"
				)).given(
						checkPersistentEntities()
				).when().then(
						sleepFor(10_000L),
						freeze().startingIn(60).seconds().andLasting(1).minutes()
								.payingWith(GENESIS)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
