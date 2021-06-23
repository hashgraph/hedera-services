package com.hedera.services.bdd.suites.crypto;


/*-
 * ‌
 * Hedera Services Node
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
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class TransferWithCustomFees extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TransferWithCustomFees.class);

	public static void main(String... args) {
		new TransferWithCustomFees().runSuiteAsync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				transferWithCustomFeeScheduleHappyPath(),
				}
		);
	}

	public HapiApiSpec transferWithCustomFeeScheduleHappyPath() {
		final var hbarAmount = 1_000_000L;
		final var hbarFee = 1_000L;
		final var tokenTotal = 1_000L;
		final var htsFee = 100L;

		final var token = "withCustomSchedules";
		final var feeDenom = "demon";
		final var hbarCollector = "hbarFee";
		final var htsCollector = "demonFee";
		final var tokenReceiver = "receiver";

		final var tokenOwner = "tokenOwner";
		final var customFeeKey = "customScheduleKey";

		return defaultHapiSpec("transferWithCustomFeeScheduleHappyPath")
				.given(
						newKeyNamed(customFeeKey),
						cryptoCreate(htsCollector),
						cryptoCreate(hbarCollector)
								.balance(0L),
						cryptoCreate(tokenReceiver),

						cryptoCreate(tokenOwner)
								.balance(hbarAmount),

						tokenCreate(feeDenom)
								.treasury(tokenOwner)
								.initialSupply(tokenTotal),

						tokenAssociate(htsCollector, feeDenom),

						tokenCreate(token)
								.treasury(tokenOwner)
								.initialSupply(tokenTotal)
								.customFeeKey(customFeeKey)
								.withCustom(fixedHbarFee(hbarFee, hbarCollector)),

						tokenAssociate(tokenReceiver, token)
				).when(
						cryptoTransfer(moving(1, token).between(tokenOwner, tokenReceiver))
								.fee(ONE_HUNDRED_HBARS)
								.payingWith(tokenOwner)

				).then(
						getAccountBalance(tokenOwner)
								.hasTokenBalance(token, 999)
								.hasTokenBalance(feeDenom, 1000),
						getAccountBalance(hbarCollector).hasTinyBars(1000)
				);
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
