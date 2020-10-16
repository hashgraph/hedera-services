package com.hedera.services.bdd.suites.utils;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;

import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.keypairs.SpecUtils;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStoreException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.stream.Collectors;

public class FetchWithPem extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FetchWithPem.class);

	public static void main(String... args) {
		new FetchWithPem().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						updateFeeSchedule(),
//						getFeeSchedule(),
//						getSystemFiles(),
						genesisBalance(),
				}
		);
	}

	private HapiApiSpec genesisBalance() {
		return customHapiSpec("GetAccountBalance")
				.withProperties(Map.of(
//						"nodes", "35.192.2.25:0.0.5",
						"nodes", "35.237.200.180:0.0.3",
						"default.payer", "0.0.950",
						"startupAccounts.path", "src/main/resource/mainnet-account950.txt"
				)).given().when().then(
//						QueryVerbs.getAccountBalance("0.0.3").logged()
						getFileInfo(EXCHANGE_RATES).logged()
				);
	}

	private HapiApiSpec updateFeeSchedule() {
		try {
			var feeScheduleLoc = "src/main/resource/testfiles/2020-04-22-FeeSchedule.bin";
			ByteString fromDisk = ByteString.copyFrom(
					Files.readAllBytes(Paths.get(feeScheduleLoc)));
			var propertiesLoc = "staging121.bin";
			ByteString propsFromDisk = ByteString.copyFrom(Files.readAllBytes(Paths.get(propertiesLoc)));

			return customHapiSpec("GetFeeSchedule")
					.withProperties(Map.of(
							"nodes", "35.237.182.66:0.0.3",
							"client.feeSchedule.fromDisk", "true",
							"client.feeSchedule.path", feeScheduleLoc
					)).given(
							getFileInfo(APP_PROPERTIES).logged(),
							fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL).contents(propsFromDisk),
							getFileInfo(APP_PROPERTIES).logged()
					).when().then(
//							UtilVerbs.updateLargeFile(GENESIS, FEE_SCHEDULE, fromDisk)
					);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private HapiApiSpec getSystemFiles() {
		var feeScheduleLoc = "src/main/resource/testfiles/2020-04-22-FeeSchedule.bin";

		return customHapiSpec("GetSystemKeys")
				.withProperties(Map.of(
						"nodes", "35.237.182.66:0.0.3",
						"client.feeSchedule.fromDisk", "true",
						"client.feeSchedule.path", feeScheduleLoc
				)).given().when().then(
						getFileContents(EXCHANGE_RATES)
								.saveTo("staging112.bin"),
						getFileContents(APP_PROPERTIES)
								.saveTo("staging121.bin")
								.saveReadableTo(
										uncheckedProps(ServicesConfigurationList::parseFrom),
										"staging-config.properties").logged(),
						getFileContents(API_PERMISSIONS)
								.saveTo("staging122.bin")
								.saveReadableTo(
										uncheckedProps(ServicesConfigurationList::parseFrom),
										"testnet-api-permission.properties").logged()
				);
	}

	private String literalFromPem(String loc) {
		try {
			var f = new File(loc);
			if (!f.exists()) {
				log.error(String.format("Missing payer PEM @ '%s', exiting.", loc));
				System.exit(1);
			}
			return SpecUtils.asSerializedOcKeystore(
					f,
					"<SECRET>",
					HapiPropertySource.asAccount("0.0.50"));
		} catch (KeyStoreException | IOException e) {
			throw new IllegalStateException(e);
		}
	}

	@FunctionalInterface
	private interface CheckedParser {
		Object parseFrom(byte[] bytes) throws Exception;
	}

	private Function<byte[], String> uncheckedProps(CheckedParser parser) {
		return bytes -> {
			try {
				var proto = parser.parseFrom(bytes);
				var jutilConfig = new Properties();
				((ServicesConfigurationList)proto).getNameValueList().forEach(setting ->
						jutilConfig.setProperty(setting.getName(), setting.getValue()));
				return jutilConfig.stringPropertyNames()
						.stream()
						.map(prop -> String.format("%s=%s", prop, jutilConfig.get(prop)))
						.collect(Collectors.joining("\n"));
			} catch (Exception e) {
				e.printStackTrace();
				return "<N/A> due to " + e.getMessage() + "!";
			}
		};
	}

	private Function<byte[], String> unchecked(CheckedParser parser) {
		return bytes -> {
			try {
				var readable = parser.parseFrom(bytes).toString();
				log.info(readable);
				return readable;
			} catch (Exception e) {
				e.printStackTrace();
				return "<N/A> due to " + e.getMessage() + "!";
			}
		};
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
