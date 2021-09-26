package com.hedera.services.bdd.suites.freeze;


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

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeUpgrade;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FREEZE_START_TIME_MUST_BE_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_UPGRADE_HAS_BEEN_PREPARED;

public class UpgradeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpgradeSuite.class);

	public static void main(String... args) {
		new UpgradeSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						freezeOnlyPrecheckRejectsInvalid(),
						freezeUpgradeValidationRejectsInvalid(),
				}
		);
	}

	private HapiApiSpec freezeOnlyPrecheckRejectsInvalid() {
		return defaultHapiSpec("freezeOnlyPrecheckRejectsInvalid")
				.given().when().then(
						freezeOnly().withRejectedStartHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedStartMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedEndHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().withRejectedEndMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeOnly().startingIn(-60).minutes().hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE)
				);
	}

	private HapiApiSpec freezeUpgradeValidationRejectsInvalid() {
		return defaultHapiSpec("freezeUpgradeValidationRejectsInvalid")
				.given().when().then(
						freezeUpgrade().withRejectedStartHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedStartMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedEndHr().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().withRejectedEndMin().hasPrecheck(INVALID_FREEZE_TRANSACTION_BODY),
						freezeUpgrade().startingIn(-60).minutes().hasPrecheck(FREEZE_START_TIME_MUST_BE_FUTURE),
						freezeUpgrade().startingIn(2).minutes().hasKnownStatus(NO_UPGRADE_HAS_BEEN_PREPARED)
				);
	}
}
