package com.hedera.services.bdd.suites.token;

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
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_REF;

public class TokenDeleteSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenDeleteSpecs.class);

	public static void main(String... args) {
		new TokenDeleteSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						deletionValidatesRef(),
						deletionWorksAsExpected(),
				}
		);
	}

	public HapiApiSpec deletionWorksAsExpected() {
		return defaultHapiSpec("DeletionWorksAsExpected")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS),
						tokenCreate("tbd")
								.payingWith("payer")
								.symbol(salted("tbd"))
				).when(
						tokenDelete("tbd")
								.payingWith("payer")
				).then(
						getTokenInfo("tbd").logged()
				);
	}

	public HapiApiSpec deletionValidatesRef() {
		return defaultHapiSpec("DeletionValidatesRef")
				.given(
						cryptoCreate("payer").balance(A_HUNDRED_HBARS)
				).when( ).then(
						tokenDelete("1.2.3")
								.payingWith("payer")
								.signedBy("payer")
								.hasKnownStatus(INVALID_TOKEN_REF)
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
