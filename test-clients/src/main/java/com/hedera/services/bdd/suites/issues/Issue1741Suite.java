package com.hedera.services.bdd.suites.issues;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;

import java.util.List;

public class Issue1741Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue1741Suite.class);

	public static void main(String... args) {
		new Issue1741Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				queryPaymentTxnMustHavePayerBalanceForBothTransferFeeAndNodePayment()
		);
	}

	public static HapiApiSpec queryPaymentTxnMustHavePayerBalanceForBothTransferFeeAndNodePayment() {
		final long BALANCE = 1_000_000L;

		return HapiApiSpec.defaultHapiSpec("QueryPaymentTxnMustHavePayerBalanceForBothTransferFeeAndNodePayment")
				.given(
						cryptoCreate("payer").balance(BALANCE)
				).when().then(
						getAccountInfo("payer")
								.nodePayment(BALANCE)
								.payingWith("payer")
								.hasAnswerOnlyPrecheck(INSUFFICIENT_PAYER_BALANCE)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
