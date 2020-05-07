package com.hedera.services.bdd.suites.fees;

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
import com.hedera.services.bdd.spec.keys.KeyLabel;
import com.hedera.services.bdd.spec.keys.SigControl;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.services.bdd.spec.keys.KeyLabel.complex;
import static com.hedera.services.bdd.spec.keys.SigControl.ANY;

import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.SigControl.threshSigs;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;

public class OverlappingKeysSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(OverlappingKeysSuite.class);

	public static void main(String... args) {
		new OverlappingKeysSuite().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
				feeCalcUsesNumPayerKeys()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
		);
	}

	private HapiApiSpec feeCalcUsesNumPayerKeys() {
		SigControl SHAPE = threshSigs(2, threshSigs(2, ANY, ANY, ANY), threshSigs(2, ANY, ANY, ANY));
		KeyLabel ONE_UNIQUE_KEY = complex(complex("X", "X", "X"), complex("X", "X", "X"));
		SigControl SIGN_ONCE = threshSigs(2, threshSigs(3, ON, OFF, OFF), threshSigs(3, OFF, OFF, OFF));

		return defaultHapiSpec("PayerSigRedundancyRecognized")
				.given(
						newKeyNamed("repeatingKey").shape(SHAPE).labels(ONE_UNIQUE_KEY),
						cryptoCreate("testAccount").key("repeatingKey").balance(1_000_000_000L)
				).when().then(
						QueryVerbs.getAccountInfo("testAccount")
								.sigControl(forKey("repeatingKey", SIGN_ONCE))
								.payingWith("testAccount")
								.numPayerSigs(5)
								.hasAnswerOnlyPrecheck(INSUFFICIENT_TX_FEE),
						QueryVerbs.getAccountInfo("testAccount")
								.sigControl(forKey("repeatingKey", SIGN_ONCE))
								.payingWith("testAccount")
								.numPayerSigs(6)
				);
	}



	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

