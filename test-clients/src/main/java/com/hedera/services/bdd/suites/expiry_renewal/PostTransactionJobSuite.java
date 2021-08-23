/*
 * -
 * ‌
 * Hedera Services Node
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *       http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.suites.expiry_renewal;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.fees.CongestionPricingSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;

public class PostTransactionJobSuite extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(CongestionPricingSuite.class);

	public static void main(String... args) {
		new PostTransactionJobSuite().runSuiteSync();
	}

	/**
	 * This test is used only to fill the system with heavy expiration entities.
	 */
	public HapiApiSpec expiredAccountTest() {
		return defaultHapiSpec("expiredAccountGetsExpired")
				.given(
						cryptoCreate("treasury").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc2").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc3").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc4").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc5").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc6").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc7").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc8").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc9").balance(0L).autoRenewSecs(50),
						cryptoCreate("acc10").balance(0L).autoRenewSecs(50),
						tokenCreate("token").treasury("treasury"),
						tokenAssociate("acc2", "token"),
						tokenAssociate("acc3", "token"),
						tokenAssociate("acc4", "token"),
						tokenAssociate("acc5", "token"),
						tokenAssociate("acc6", "token"),
						tokenAssociate("acc7", "token"),
						tokenAssociate("acc8", "token"),
						tokenAssociate("acc9", "token"),
						tokenAssociate("acc10", "token"),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc2")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc3")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc4")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc5")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc6")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc7")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc8")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc9")),
						cryptoTransfer(TokenMovement.moving(10, "token").between("treasury", "acc10"))
				)
				.when()
				.then(
						getAccountInfo("acc1").logged(),
						getAccountInfo("acc2").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				expiredAccountTest()
		);
	}
}
