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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class ImmutableContractSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ImmutableContractSuite.class);
	final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";

	public static void main(String... args) {
		/* Has a static initializer whose behavior seems influenced by initialization of ForkJoinPool#commonPool. */
		new org.ethereum.crypto.HashUtil();

		new ImmutableContractSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveSpecs(),
				negativeSpecs()
		);
	}

	List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
				cannotDeleteImmutableContract(),
				cannotUpdateKeyOnImmutableContract()
		);
	}

	List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(
				canExtendExpiryForImmutableContract()
		);
	}

	private HapiApiSpec cannotDeleteImmutableContract() {
		return defaultHapiSpec("CannotDeleteImmutableContract")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("immutableContract").bytecode("bytecode").omitAdminKey()
				).when().then(
						contractDelete("immutableContract").hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec cannotUpdateKeyOnImmutableContract() {
		return defaultHapiSpec("CannotUpdateKeyOnImmutableContract")
				.given(
						newKeyNamed("shouldBeUnusable"),
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("immutableContract").bytecode("bytecode").omitAdminKey()
				).when().then(
						contractUpdate("immutableContract")
								.newKey("shouldBeUnusable")
								.hasKnownStatus(MODIFYING_IMMUTABLE_CONTRACT)
				);
	}

	private HapiApiSpec canExtendExpiryForImmutableContract() {
		return defaultHapiSpec("CanExtendExpiryForImmutableContract")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE),
						contractCreate("immutableContract").bytecode("bytecode").omitAdminKey()
				).when().then(
						contractUpdate("immutableContract").newExpirySecs(DEFAULT_PROPS.defaultExpirationSecs() * 2)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
