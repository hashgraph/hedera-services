package com.hedera.services.bdd.suites.misc;

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

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class ReviewMainnetEntities extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ReviewMainnetEntities.class);

	public static void main(String... args) throws Exception {
		new ReviewMainnetEntities().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						reviewObjects(),
//						checkTls(),
//						xfer(),
						doSomething(),
				}
		);
	}

	private HapiApiSpec doSomething() {
		final String NODES = "35.237.208.135:0.0.3";
		return customHapiSpec("xfer")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/MainnetStartupAccount.txt"
				)).given(
						cryptoCreate("somebody").balance(1L)
				).when().then(
						withOpContext((spec, opLog) -> {
							byte[] passphraseBytes = new byte[16];
							new SplittableRandom().nextBytes(passphraseBytes);
							KeyFactory.PEM_PASSPHRASE = Base64.encodeBase64String(passphraseBytes);
							spec.keys().exportSimpleKey(String.format("somebody.pem"), "somebody");
							var loc = String.format("somebody-passphrase.txt");
							try (BufferedWriter out = Files.newWriter(new File(loc), Charset.defaultCharset())) {
								out.write(KeyFactory.PEM_PASSPHRASE);
							}
						})
				);
	}

	private HapiApiSpec xfer() {
		final String NODES = "35.237.208.135:0.0.3";
		return customHapiSpec("xfer")
				.withProperties(Map.of(
						"nodes", NODES,
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/MainnetStartupAccount.txt"
				)).given(
				).when().then(
						getAccountBalance(GENESIS).logged(),
						getAccountInfo("0.0.58").logged()
				);
	}

	private HapiApiSpec checkTls() {
		final String MAINNET_NODES = "35.237.200.180:0.0.3,35.186.191.247:0.0.4," +
				"35.192.2.25:0.0.5,35.199.161.108:0.0.6,35.203.82.240:0.0.7," +
				"35.236.5.219:0.0.8,35.197.192.225:0.0.9,35.242.233.154:0.0.10," +
				"35.240.118.96:0.0.11,35.204.86.32:0.0.12,35.234.132.107:0.0.13," +
				"35.236.2.27:0.0.14,35.228.11.53:0.0.15";
		return customHapiSpec("CheckTls")
				.withProperties(Map.of(
						"nodes", MAINNET_NODES,
						"tls", "on",
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/MainnetStartupAccount.txt",
						"client.feeSchedule.fromDisk", "true",
						"client.feeSchedule.path", "system-files/feeSchedule.bin",
						"client.exchangeRates.fromDisk", "true",
						"client.exchangeRates.path", "system-files/exchangeRates.bin"
				)).given(
				).when().then(
						IntStream.range(3, 16).mapToObj(i ->
								getFileInfo("0.0.101")
										.setNode(String.format("0.0.%d", i))
										.logged()
						).toArray(HapiSpecOperation[]::new)
				);
	}

	private HapiApiSpec reviewObjects() {
		long TINYBARS_PER_HBAR = 100_000_000L;

		return customHapiSpec("ReviewObjects")
				.withProperties(Map.of(
						"nodes", "35.237.200.180:0.0.3,35.186.191.247:0.0.4," +
//								"35.192.2.25:0.0.5,35.199.161.108:0.0.6," +
								"35.203.82.240:0.0.7," +
								"35.236.5.219:0.0.8,35.197.192.225:0.0.9,35.242.233.154:0.0.10," +
								"35.240.118.96:0.0.11,35.204.86.32:0.0.12,35.234.132.107:0.0.13," +
//								"35.236.2.27:0.0.14," +
								"35.228.11.53:0.0.15",
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/MainnetStartupAccount.txt",
						"client.feeSchedule.fromDisk", "true",
						"client.feeSchedule.path", "system-files/feeSchedule.bin",
						"client.exchangeRates.fromDisk", "true",
						"client.exchangeRates.path", "system-files/exchangeRates.bin"
				)).given(
//						TxnVerbs.cryptoTransfer(HapiCryptoTransfer.tinyBarsFromTo(
//								GENESIS, ADDRESS_BOOK_CONTROL, 100 * TINYBARS_PER_HBAR))
				).when().then(
						getFileInfo("0.0.101").logged()
//						getAccountBalance(GENESIS).logged(),
//						getAccountBalance("0.0.55").logged()
//						getAccountBalance("0.0.39281").logged(),
//						getAccountBalance("0.0.45385").logged()
//						/* File meta */
//						getFileInfo("0.0.39283").logged(),
//						/* Topic meta */
//						getTopicInfo("0.0.39286").logged(),
//						/* Contract meta */
//						getFileInfo("0.0.39290").logged(),
//						getContractInfo("0.0.39291").logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
