package com.hedera.services.bdd.suites.contract;

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

import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;

import java.util.List;

public class ContractUpdateSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractUpdateSuite.class);

	public static void main(String... args) {
		new ContractUpdateSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[]{
				updateWithPendingNewKeySucceeds(),
				canSetImmutableWithEmptyKeyList(),
		});
	}

	private HapiApiSpec updateWithPendingNewKeySucceeds() {
		return defaultHapiSpec("UpdateWithPendingNewKeySucceeds")
				.given(
						newKeyNamed("newKey"),
						fileCreate("bytecode").path(PATH_TO_LOOKUP_BYTECODE),
						contractCreate("target").bytecode("bytecode")
				).when(
						contractUpdate("target").newKey("newKey").deferStatusResolution()
				).then(
						contractUpdate("target")
								.via("txnRequiringSyncVerify")
								.signedBy(GENESIS, "newKey")
								.newMemo("So we outdanced thought...")
				);
	}

	private HapiApiSpec canSetImmutableWithEmptyKeyList() {
		return defaultHapiSpec("CanSetImmutableWithEmptyKeyList")
				.given(
						newKeyNamed("pristine"),
						contractCreate("toBeImmutable")
				).when(
						contractUpdate("toBeImmutable").improperlyEmptyingAdminKey()
								.hasKnownStatus(INVALID_ADMIN_KEY),
						contractUpdate("toBeImmutable").properlyEmptyingAdminKey()
				).then(
						contractUpdate("toBeImmutable").newKey("pristine")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
