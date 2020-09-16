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

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.balanceSnapshot;
import static com.hedera.services.bdd.spec.assertions.AccountInfoAsserts.changeFromSnapshot;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;

import com.hedera.services.bdd.spec.infrastructure.meta.ContractCallDetails;
import com.hedera.services.bdd.spec.infrastructure.meta.SupportedContract;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;

public class RandomOps extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RandomOps.class);

	public static void main(String... args) {
		new RandomOps().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						freezeWorks(),
//						createThenTransferThenUpdateDeleteThenUpdate()
				}
		);
	}

	private HapiApiSpec freezeWorks() {
		return customHapiSpec("FreezeWorks")
				.withProperties(Map.of(
						"nodes", "127.0.0.1:50213:0.0.3,127.0.0.1:50214:0.0.4,127.0.0.1:50215:0.0.5"
				)).given().when(
				).then(
						freeze().startingIn(60).seconds().andLasting(1).minutes()
				);
	}

	private HapiApiSpec createThenTransferThenUpdateDeleteThenUpdate() {
		return defaultHapiSpec("createThenTransferThenUpdateDeleteThenUpdate")
				.given(
						newKeyNamed("bombKey"),
						cryptoCreate("sponsor").sendThreshold(1L),
						cryptoCreate("beneficiary"),
						cryptoCreate("tbd"),
						fileCreate("bytecode").path(SupportedContract.inPath("simpleStorage")),
						contractCreate("simpleStorage").bytecode("bytecode")
				).when(
						contractCall("simpleStorage",
								ContractCallDetails.SIMPLE_STORAGE_SETTER_ABI,
								BigInteger.valueOf(1)),
						cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1_234L))
								.payingWith("sponsor")
								.memo("Hello World!")
				).then(
						cryptoUpdate("beneficiary").key("bombKey"),
						sleepFor(2_000),
						cryptoDelete("tbd"),
						sleepFor(2_000),
						cryptoUpdate("beneficiary").key("bombKey")
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
