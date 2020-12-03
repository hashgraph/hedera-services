package com.hedera.services.bdd.suites.utils.validation;

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
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.JutilPropsToSvcCfgBytes;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class OneOffValidation extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(OneOffValidation.class);

	private static final String STAGINGLG_NODES = "35.237.182.66:0.0.3,35.245.226.22:0.0.4,34.68.9.203:0.0.5," +
			"34.83.131.197:0.0.6,34.94.236.63:0.0.7,35.203.26.115:0.0.8,34.77.3.213:0.0.9," +
			"35.197.237.44:0.0.10,35.246.250.176:0.0.11,34.90.117.105:0.0.12,35.200.57.21:0.0.13," +
			"34.92.120.143:0.0.14,34.87.47.168:0.0.15";
	private static final String STAGINGLG_BOOTSTRAP_ACCOUNT = "0.0.950";
	private static final String STAGINGLG_BOOTSTRAP_ACCOUNT_LOC = "src/main/resource/MainnetStartupAccount.txt";

	private static final String MAINNET_NODES = "35.237.200.180:0.0.3,35.186.191.247:0.0.4," +
			"35.192.2.25:0.0.5,35.199.161.108:0.0.6,35.203.82.240:0.0.7," +
			"35.236.5.219:0.0.8,35.197.192.225:0.0.9,35.242.233.154:0.0.10," +
			"35.240.118.96:0.0.11,35.204.86.32:0.0.12,35.234.132.107:0.0.13," +
			"35.236.2.27:0.0.14,35.228.11.53:0.0.15";
	private static final String MAINNET_BOOTSTRAP_ACCOUNT = "0.0.950";
	private static final String MAINNET_BOOTSTRAP_ACCOUNT_LOC = "src/main/resource/MainnetStartupAccount.txt";

	private static final String TESTNET_NODES = "34.94.106.61:0.0.3,35.196.34.86:0.0.4," +
			"35.194.75.187:0.0.5,34.82.241.226:0.0.6";
	private static final String TESTNET_BOOTSTRAP_ACCOUNT = "0.0.50";
	private static final String TESTNET_CIVILIAN_BOOTSTRAP_ACCOUNT = "0.0.65221";
	private static final String TESTNET_BOOTSTRAP_ACCOUNT_LOC = "src/main/resource/TestnetStartupAccount.txt";
	private static final String TESTNET_CIVILIAN_BOOTSTRAP_ACCOUNT_LOC = "src/main/resource/TestnetCivilianStartupAccount.txt";

	private static final String NODES = TESTNET_NODES;
//	private static final String NODES = MAINNET_NODES;
//	private static final String BOOTSTRAP_ACCOUNT = TESTNET_BOOTSTRAP_ACCOUNT;
//	private static final String BOOTSTRAP_ACCOUNT = MAINNET_BOOTSTRAP_ACCOUNT;
	private static final String BOOTSTRAP_ACCOUNT = TESTNET_CIVILIAN_BOOTSTRAP_ACCOUNT;
//	private static final String BOOTSTRAP_ACCOUNT_LOC = TESTNET_BOOTSTRAP_ACCOUNT_LOC;
//	private static final String BOOTSTRAP_ACCOUNT_LOC = MAINNET_BOOTSTRAP_ACCOUNT_LOC;
	private static final String BOOTSTRAP_ACCOUNT_LOC = TESTNET_CIVILIAN_BOOTSTRAP_ACCOUNT_LOC;

	public static void main(String... args) {
		new OneOffValidation().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//				xferWithTls(),
//				bootstrapBalanceCheck(),
//				createAnAccount(),
//				checkAppProperties(),
				createAContract(),
		});
	}

	private HapiApiSpec createAContract() {
		return defaultHapiSpec("CreateAContract").given(
				fileCreate("bytecode").path(ContractResources.MULTIPURPOSE_BYTECODE_PATH)
		).when(
				contractCreate("something").bytecode("bytecode").via("createTxn")
		).then(
				getTxnRecord("createTxn").logged()
		);
	}

	private HapiApiSpec checkAppProperties() {
		var serde = new JutilPropsToSvcCfgBytes("application.properties");
		var binLoc = "throwaway.bin";
		var propertiesLoc = "throwaway-application.properties";

		return customHapiSpec("CheckAppProperties").withProperties(Map.of(
				"nodes", STAGINGLG_NODES,
				"default.payer", STAGINGLG_BOOTSTRAP_ACCOUNT,
				"startupAccounts.path", STAGINGLG_BOOTSTRAP_ACCOUNT_LOC
		)).given().when().then(
				getFileContents(APP_PROPERTIES)
						.saveTo(binLoc)
						.saveReadableTo(serde::fromRawFile, propertiesLoc)
		);
	}

	private HapiApiSpec createAnAccount() {
		return customHapiSpec("CreateAnAccount").withProperties(Map.of(
				"nodes", NODES,
				"default.payer", BOOTSTRAP_ACCOUNT,
				"startupAccounts.path", BOOTSTRAP_ACCOUNT_LOC
		)).given(
				newKeyNamed("misc"),
				withOpContext((spec, opLog) -> {
					spec.keys().exportSimpleKey("testnet-account?.pem", "misc");
				})
		).when().then(
				cryptoCreate("bootstrap").key("misc")
		);
	}

	private HapiApiSpec xferWithTls() {
		return customHapiSpec("tryWithTls").withProperties(Map.of(
				"nodes", NODES,
				"default.payer", BOOTSTRAP_ACCOUNT,
				"startupAccounts.path", BOOTSTRAP_ACCOUNT_LOC
		)).given().when().then(
				cryptoTransfer(tinyBarsFromTo(GENESIS, ADDRESS_BOOK_CONTROL, 1))
		);
	}

	private HapiApiSpec bootstrapBalanceCheck() {
		final String DEFAULT_DIR = "./system-files";

		return customHapiSpec("balanceCheck").withProperties(Map.of(
				"nodes", NODES,
				"default.payer", BOOTSTRAP_ACCOUNT,
				"startupAccounts.path", BOOTSTRAP_ACCOUNT_LOC,
				"client.feeSchedule.fromDisk", "true",
				"client.feeSchedule.path", path(DEFAULT_DIR, "feeSchedule.bin"),
				"client.exchangeRates.fromDisk", "true",
				"client.exchangeRates.path", path(DEFAULT_DIR, "exchangeRates.bin")
		)).given().when().then(
				QueryVerbs.getAccountInfo(GENESIS).logged()
		);
	}

	private String path(String prefix, String file) {
		return Path.of(prefix, file).toString();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

