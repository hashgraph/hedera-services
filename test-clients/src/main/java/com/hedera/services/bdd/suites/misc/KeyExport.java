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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class KeyExport extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(KeyExport.class);

	private static final String PEM_FILE_NAME = "previewTestnet.pem";

	public static void main(String... args) throws Exception {
		new KeyExport().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						exportCurrentTreasuryKey(),
//						exportGenesisKey(),
//						validateNewKey(),
				}
		);
	}

	private HapiApiSpec validateNewKey() {
		return defaultHapiSpec("validateNewKey").given(
				keyFromPem(PEM_FILE_NAME)
						.name("newKey")
						.passphrase("P1WUX2Xla2wFslpoPTN39avz")
						.simpleWacl()
		).when().then(
				fileCreate("testFile").key("newKey")
		);
	}

	private HapiApiSpec exportCurrentTreasuryKey() {
		KeyFactory.PEM_PASSPHRASE = "passphrase";

		return defaultHapiSpec("ExportCurrentTreasuryKeyAsPem")
				.given( ).when( ).then(
						withOpContext((spec, opLog) -> {
							spec.keys().exportSimpleWacl("devGenesisKeypair.pem", GENESIS);
						})
				);
	}

	private HapiApiSpec exportGenesisKey() {
		final var r = new Random();
		final int passphraseLength = 24;
		final char[] choices = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
		final KeyShape listOfSizeOne = listOf(1);

		KeyFactory.PEM_PASSPHRASE = IntStream.range(0, passphraseLength)
				.map(ignore -> r.nextInt(choices.length))
				.mapToObj(i -> Character.valueOf(choices[i]).toString())
				.collect(Collectors.joining(""));

		return defaultHapiSpec("ExportGenesisKey")
				.given(
						newKeyNamed("ab-initio").shape(listOfSizeOne)
				).when( ).then(
						withOpContext((spec, opLog) -> {
							opLog.info("Passphrase is: {}", KeyFactory.PEM_PASSPHRASE);
							spec.keys().exportSimpleWacl(PEM_FILE_NAME, "ab-initio");
						})
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
