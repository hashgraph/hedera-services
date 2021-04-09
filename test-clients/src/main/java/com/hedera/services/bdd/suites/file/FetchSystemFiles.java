package com.hedera.services.bdd.suites.file;

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
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;

public class FetchSystemFiles extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FetchSystemFiles.class);

	public static void main(String... args) {
		new FetchSystemFiles().runSuiteSync();
	}

	final String DEFAULT_DIR = "./system-files";
	final String TARGET_DIR = "./remote-system-files";

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				fetchFiles()
		);
	}

	private HapiApiSpec fetchFiles() {
		return customHapiSpec("FetchFiles")
				.withProperties(Map.of(
						"client.feeSchedule.fromDisk", "true",
						"client.feeSchedule.path", path(DEFAULT_DIR, "feeSchedule.bin"),
						"client.exchangeRates.fromDisk", "true",
						"client.exchangeRates.path", path(DEFAULT_DIR, "exchangeRates.bin")
				)).given().when().then(
						getFileContents(NODE_DETAILS)
								.saveTo(path("nodeDetails.bin"))
								.saveReadableTo(unchecked(NodeAddressBook::parseFrom), path("nodeDetails.txt")),
						getFileContents(ADDRESS_BOOK)
								.saveTo(path("addressBook.bin"))
								.saveReadableTo(unchecked(NodeAddressBook::parseFrom), path("addressBook.txt")),
						getFileContents(NODE_DETAILS)
								.saveTo(path("nodeDetails.bin"))
								.saveReadableTo(unchecked(NodeAddressBook::parseFrom), path("nodeDetails.txt")),
						getFileContents(EXCHANGE_RATES)
								.saveTo(path("exchangeRates.bin"))
								.saveReadableTo(unchecked(ExchangeRateSet::parseFrom), path("exchangeRates.txt")),
						getFileContents(APP_PROPERTIES)
								.saveTo(path("appProperties.bin"))
								.saveReadableTo(
										unchecked(ServicesConfigurationList::parseFrom),
										path("appProperties.txt")),
						getFileContents(API_PERMISSIONS)
								.saveTo(path("apiPermissions.bin"))
								.saveReadableTo(
										unchecked(ServicesConfigurationList::parseFrom),
										path("appPermissions.txt")),
						getFileContents(FEE_SCHEDULE)
								.saveTo(path("feeSchedule.bin"))
								.fee(300_000L)
								.nodePayment(40L)
								.saveReadableTo(
										unchecked(CurrentAndNextFeeSchedule::parseFrom),
										path("feeSchedule.txt"))
						);
	}

	@FunctionalInterface
	public interface CheckedParser {
		Object parseFrom(byte[] bytes) throws Exception;
	}

	public static Function<byte[], String> unchecked(CheckedParser parser) {
			return bytes -> {
				try {
					return parser.parseFrom(bytes).toString();
				} catch (Exception e) {
					e.printStackTrace();
					return "<N/A> due to " + e.getMessage() + "!";
				}
			};
	}

	private String path(String file) {
		return Path.of(TARGET_DIR, file).toString();
	}

	private String path(String prefix, String file) {
		return Path.of(prefix, file).toString();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
