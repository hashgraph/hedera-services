package com.hedera.services.yahcli.suites;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.hapiFreeze;

public class FreezeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeSuite.class);

	private final Instant freezeStartTime;

	private final Map<String, String> specConfig;

	public FreezeSuite(final Map<String, String> specConfig, final Instant freezeStartTime) {
		this.specConfig = specConfig;
		this.freezeStartTime = freezeStartTime;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				freezeSystem(freezeStartTime)
		});
	}

	private HapiApiSpec freezeSystem(final Instant freezeStartTime) {
		return HapiApiSpec.customHapiSpec(("freezeSystem"))
				.withProperties(specConfig)
				.given().when()
				.then(
						hapiFreeze(freezeStartTime)
								.noLogging()
								.yahcliLogging()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}