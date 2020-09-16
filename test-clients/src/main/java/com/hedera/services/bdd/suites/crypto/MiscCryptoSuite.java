package com.hedera.services.bdd.suites.crypto;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;

public class MiscCryptoSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(MiscCryptoSuite.class);

	public static void main(String... args) {
		new MiscCryptoSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
//				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
//				transferChangesBalance()
				getsGenesisBalance()
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return List.of(
				updateWithOutOfDateKeyFails()
		);
	}

	public static HapiApiSpec getsGenesisBalance() {
		return defaultHapiSpec("GetsGenesisBalance")
				.given().when().then(
						getAccountBalance(GENESIS).logged()
				);
	}

	public static HapiApiSpec transferChangesBalance() {
		return defaultHapiSpec("TransferChangesBalance")
				.given(
						cryptoCreate("newPayee").balance(0L)
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "newPayee", 1_000_000_000L)
						)
				).then(
						getAccountBalance("newPayee").hasTinyBars(1_000_000_000L).logged()
				);
	}

	private HapiApiSpec updateWithOutOfDateKeyFails() {
		return defaultHapiSpec("UpdateWithOutOfDateKeyFails")
				.given(
						newKeyNamed("originalKey"), newKeyNamed("updateKey"),
						cryptoCreate("targetAccount").key("originalKey")
				).when(
						cryptoUpdate("targetAccount").key("updateKey").deferStatusResolution(),
						cryptoUpdate("targetAccount").receiverSigRequired(true)
								.signedBy(GENESIS, "originalKey")
								.via("invalidKeyUpdateTxn").deferStatusResolution().hasAnyKnownStatus(),
						sleepFor(1_000L)
				).then(
						getTxnRecord("invalidKeyUpdateTxn").hasPriority(recordWith().status(INVALID_SIGNATURE))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
