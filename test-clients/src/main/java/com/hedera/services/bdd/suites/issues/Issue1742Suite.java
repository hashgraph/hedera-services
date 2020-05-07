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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;

import java.util.List;

public class Issue1742Suite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue1742Suite.class);

	public static void main(String... args) {
		new Issue1742Suite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				cryptoTransferListShowsOnlyFeesAfterIAB()
		);
	}

	@Override
	public boolean leaksState() {
		return true;
	}

	public static HapiApiSpec cryptoTransferListShowsOnlyFeesAfterIAB() {
		final long PAYER_BALANCE = 1_000_000L;

		return defaultHapiSpec("CryptoTransferListShowsOnlyFeesAfterIAB")
				.given(flattened(
						cryptoCreate("payer").balance(PAYER_BALANCE),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS, "payer")
				)).when(
						cryptoTransfer(
								tinyBarsFromTo("payer", GENESIS, PAYER_BALANCE)
						).payingWith("payer").via("txn").hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE)
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS, "payer"))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
