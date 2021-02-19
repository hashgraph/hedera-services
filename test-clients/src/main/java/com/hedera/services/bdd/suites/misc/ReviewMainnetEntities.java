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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getContractInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.burnToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.revokeTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenDissociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.wipeTokenAccount;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

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
//						doSomething(),
//						oneOfEveryTokenTxn(),
						customPayerOp(),
				}
		);
	}

	private HapiApiSpec oneOfEveryTokenTxn() {
		String A_TOKEN = "mine";
		String FIRST_USER = "somebody";

		return customHapiSpec("DoSomethingDefault")
				.withProperties(Map.of(
						"nodes", "35.231.208.148",
						"startupAccounts.path", "src/main/resource/Preview2StartupAccount.txt"
				)).given(
						newKeyNamed("firstAdminKey"),
						newKeyNamed("secondAdminKey"),
						newKeyNamed("kycKey"),
						newKeyNamed("wipeKey"),
						newKeyNamed("freezeKey"),
						newKeyNamed("supplyKey"),
						cryptoCreate(TOKEN_TREASURY),
						cryptoCreate(FIRST_USER)
				).when(
						tokenCreate(A_TOKEN)
								.treasury(TOKEN_TREASURY)
								.adminKey("firstAdminKey")
								.freezeKey("freezeKey")
								.freezeDefault(true)
								.kycKey("kycKey")
								.wipeKey("wipeKey")
								.supplyKey("supplyKey"),
						tokenAssociate(FIRST_USER, A_TOKEN),
						tokenUnfreeze(A_TOKEN, FIRST_USER),
						grantTokenKyc(A_TOKEN, FIRST_USER)
				).then(
						tokenUpdate(A_TOKEN).adminKey("secondAdminKey"),
						cryptoTransfer(moving(10, A_TOKEN).between(TOKEN_TREASURY, FIRST_USER)),
						wipeTokenAccount(A_TOKEN, FIRST_USER, 10),
						mintToken(A_TOKEN, 10),
						burnToken(A_TOKEN, 10),
						tokenFreeze(A_TOKEN, FIRST_USER),
						revokeTokenKyc(A_TOKEN, FIRST_USER),
						tokenUnfreeze(A_TOKEN, FIRST_USER),
						tokenDissociate(FIRST_USER, A_TOKEN)
				);
	}

	private HapiApiSpec doSomething() {
		final String PROXIES = "35.237.194.97:0.0.3,35.186.171.76:0.0.4,35.194.0.222:0.0.5,35.197.115.53:0.0.6," +
				"35.236.103.219:0.0.7,35.203.12.99:0.0.8,34.76.93.218:0.0.9,34.89.9.244:0.0.10," +
				"34.107.118.148:0.0.11,34.91.239.212:0.0.12,35.200.11.146:0.0.13," +
				"34.96.218.21:0.0.14,35.240.220.53:0.0.15";
		final String NODES = "35.237.208.135:0.0.3,35.245.226.22:0.0.4,"
				+ "34.68.9.203:0.0.5,34.83.131.197:0.0.6";
		final String DIRECT_NODES = "35.237.194.97:0.0.3,13.71.127.1:0.0.6,27.110.33.145:0.0.7,20.49.137.94:0.0.12," +
				"35.245.226.22:0.0.4,34.72.55.137:0.0.5,35.203.26.115:0.0.8,34.77.3.213:0.0.9," + // Ubuntu
				"35.197.237.44:0.0.10,35.246.250.176:0.0.11,35.200.57.21:0.0.13,34.92.120.143:0.0.14,34.87.47.168:0.0" +
				".15"; // CentOS
		final String DIRECT_MAINNET = "50.28.79.14:0.0.3";

		return customHapiSpec("xfer")
				.withProperties(Map.of(
						"nodes", DIRECT_MAINNET,
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/MainnetStartupAccount.txt"
				)).given().when().then(
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1L))
				);
	}

	private HapiApiSpec customPayerOp() {
		final String MAINNET_NODES = "35.237.200.180:0.0.3";
		final String payer = "0.0.107630";
		final String payerWords = "<secret>";

		final long ONE_HBAR = 100_000_000L;

		return customHapiSpec("xfer")
				.withProperties(Map.of(
						"nodes", MAINNET_NODES,
						"fees.fixedOffer", "" + ONE_HBAR,
						"fees.useFixedOffer", "false",
						"default.payer", payer,
						"default.payer.mnemonic", payerWords
				)).given(
						getAccountBalance(payer).logged()
				).when(
						cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "0.0.950", 1))
								.signedBy(DEFAULT_PAYER)
								.logged()
				).then(
						getAccountBalance(payer).logged()
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
