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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.initializeSettings;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFileId;
import static com.hedera.services.bdd.suites.freeze.CommonUpgradeResources.upgradeFilePath;

public final class UpdateFileForUpgrade extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpdateFileForUpgrade.class);

	public static void main(String... args) {
		new UpdateFileForUpgrade().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				updateFileForUpgrade()
		});
	}

	private HapiApiSpec updateFileForUpgrade() {
		return defaultHapiSpec("UpdateFileForUpgrade")
				.given(
						initializeSettings()
				).when(
						fileUpdate(upgradeFileId())
								.path(upgradeFilePath())
								.signedBy(GENESIS)
								.payingWith(GENESIS)
				).then();
	}
}
