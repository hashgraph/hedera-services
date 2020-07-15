package com.hedera.services.bdd.suites.file.negative;

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
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.management.InstanceNotFoundException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class CreateFailuresSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CreateFailuresSpec.class);

	public static void main(String... args) {
		new CreateFailuresSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						handleRejectsMissingWacl(),
						precheckRejectsBadEffectiveAutoRenewPeriod(),
				}
		);
	}

	private HapiApiSpec handleRejectsMissingWacl() {
		return defaultHapiSpec("handleRejectsMissingWacl")
				.given(
						withOpContext((spec, opLog) -> {
							spec.registry().saveKey(
									"emptyKey",
									Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build());
						})
				).when( ).then(
						fileCreate("notHere")
								.contents("Not meant to be!")
								.key("emptyKey")
								.signedBy(GENESIS)
								.hasKnownStatus(ResponseCodeEnum.NO_WACL_KEY)
				);
	}

	private HapiApiSpec precheckRejectsBadEffectiveAutoRenewPeriod() {
		var now = Instant.now();
		System.out.println(now.getEpochSecond());

		return defaultHapiSpec("precheckRejectsBadEffectiveAutoRenewPeriod")
				.given( ).when( ).then(
						fileCreate("notHere")
								.lifetime(-60L)
								.hasPrecheck(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
