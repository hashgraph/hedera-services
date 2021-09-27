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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeAbort;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;

public class FreezeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeSuite.class);

	private final Instant freezeStartTime;
	private final boolean isAbort;
	private final boolean isNmtUpgrade;

	private final Map<String, String> specConfig;

	public FreezeSuite(
			final Map<String, String> specConfig,
			final Instant freezeStartTime,
			final boolean isAbort,
			final boolean isNmtUpgrade
	) {
		this.isAbort = isAbort;
		this.isNmtUpgrade = isNmtUpgrade;
		this.specConfig = specConfig;
		this.freezeStartTime = freezeStartTime;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				doFreeze()
		});
	}

	private HapiApiSpec doFreeze() {
		return HapiApiSpec.customHapiSpec("DoFreeze")
				.withProperties(specConfig)
				.given( ).when( ).then(
						requestedFreezeOp()
				);
	}

	private HapiSpecOperation requestedFreezeOp() {
		if (isAbort) {
			return freezeAbort().noLogging().yahcliLogging();
		} else {
			return isNmtUpgrade
					? freezeUpgrade().startingAt(freezeStartTime).noLogging()
					: freezeOnly().startingAt(freezeStartTime).noLogging();
		}
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}