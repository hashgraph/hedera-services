package com.hedera.services.bdd.suites.misc;

/*-
 * ‌
 * Hedera Services Test Clients
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
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

/**
 * Given a state loaded from a preprod network (usually stable testnet), we
 * want to "re-key" the treasury account for use in dev migration testing.
 */
public class RekeySavedStateTreasury extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RekeySavedStateTreasury.class);

	public static void main(String... args) {
		new RekeySavedStateTreasury().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						rekeyTreasury(),
				}
		);
	}

	static final String newTreasuryPemLoc = "dev-stabletestnet-account2.pem";
	static final String newTreasuryPassphrase = "passphrase";
	static final String devKeyPemLoc = "devGenesisKeypair.pem";

	private HapiApiSpec rekeyTreasury() {
		final var pemLocForOriginalTreasuryKey = "stabletestnet-account2.pem";
		final var passphraseForOriginalPemLoc = "<SECRET>";

//		final var hexedNewEd25519PrivateKey = CommonUtils.hex(randomUtf8Bytes(32));
		final var newTreasuryKey = "newTreasuryKey";

		return customHapiSpec("RekeyTreasury")
				.withProperties(Map.of(
						"nodes", "localhost",
						"default.payer", "0.0.2",
						"default.payer.pemKeyLoc", pemLocForOriginalTreasuryKey,
						"default.payer.pemKeyPassphrase", passphraseForOriginalPemLoc
				))
				.given(
						/* Use this for reusing the pem file. */
						keyFromPem(devKeyPemLoc)
								.passphrase(newTreasuryPassphrase)
								.name(newTreasuryKey),
//						keyFromLiteral(newTreasuryKey, hexedNewEd25519PrivateKey),
						withOpContext((spec, opLog) ->
								spec.keys().exportSimpleKey(newTreasuryPemLoc, newTreasuryKey))
				).when().then(
						cryptoUpdate(DEFAULT_PAYER).key(newTreasuryKey)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
