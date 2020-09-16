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

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.keys.SigControl.*;
import static com.hedera.services.bdd.spec.keys.ControlForKey.*;
import static com.hedera.services.bdd.spec.keys.SigMapGenerator.Nature.*;
import static com.hedera.services.bdd.spec.keys.KeyGenerator.Nature.*;

public class ContractGetBytecodeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ContractGetBytecodeSuite.class);

	public static void main(String... args) {
		new ContractGetBytecodeSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
			negativeSpecs(),
			positiveSpecs()
		);
	}

	private List<HapiApiSpec> negativeSpecs() {
		return Arrays.asList(
			invalidContractFromCostAnswer(),
			invalidContractFromAnswerOnly()
		);
	}

	private List<HapiApiSpec> positiveSpecs() {
		return Arrays.asList(
			vanillaSuccess()
		);
	}

	private HapiApiSpec vanillaSuccess() {
		SigControl controller = threshSigs(2, ON, ON, listSigs(ON, ON, ON));

		return HapiApiSpec.defaultHapiSpec("VanillaSuccess")
				.given(
						TxnVerbs.contractCreate("defaultContract")
								.adminKeyShape(controller)
								.ed25519Keys(WITH_OVERLAPPING_PREFIXES)
								.sigControl(forKey("defaultContract", controller))
								.sigMapPrefixes(UNIQUE)
				).when().then(
						QueryVerbs.getContractBytecode("defaultContract").isNonEmpty());
	}

	private HapiApiSpec invalidContractFromCostAnswer() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						QueryVerbs.getContractBytecode(invalidContract)
							.hasCostAnswerPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}

	private HapiApiSpec invalidContractFromAnswerOnly() {
		String invalidContract = HapiSpecSetup.getDefaultInstance().invalidContractName();

		return defaultHapiSpec("InvalidContract")
				.given().when().then(
						QueryVerbs.getContractBytecode(invalidContract)
								.nodePayment(27_159_182L)
								.hasAnswerOnlyPrecheck(ResponseCodeEnum.INVALID_CONTRACT_ID));
	}
}
