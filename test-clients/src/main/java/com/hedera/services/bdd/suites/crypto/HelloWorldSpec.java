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

import com.google.common.io.Files;
import com.hedera.services.bdd.spec.HapiApiSpec;

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
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.commons.codec.binary.Base64;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.List;
import java.util.SplittableRandom;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;

public class HelloWorldSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(HelloWorldSpec.class);

	public static void main(String... args) {
		new HelloWorldSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						balancesChangeOnTransfer(),
//						freezeWorks(),
//						createThenTransferThenUpdateDeleteThenUpdate()
//						createSomebodyAndExportKey(),
//						changeSomebody(),
				}
		);
	}

	private HapiApiSpec changeSomebody() {
		return defaultHapiSpec("ChangeSomebody")
				.given(
						keyFromPem("misc.pem")
								.name("tbd")
								.passphrase("ZSiN0WfqU8/PY88N/9DwxA==")
								.linkedTo("0.0.1001")
				).when(
				).then(
						cryptoUpdate("0.0.1001").receiverSigRequired(true),
						cryptoUpdate("0.0.1001").receiverSigRequired(false)
				);
	}

	private HapiApiSpec createSomebodyAndExportKey() {
		return defaultHapiSpec("CreateSomebodyAndExportKey")
				.given(
						cryptoCreate("misc")
				).when(
				).then(
						withOpContext((spec, opLog) -> {
							byte[] passphraseBytes = new byte[16];
							new SplittableRandom().nextBytes(passphraseBytes);
							KeyFactory.PEM_PASSPHRASE = Base64.encodeBase64String(passphraseBytes);
							spec.keys().exportSimpleKey(String.format("misc.pem"), "misc");
							var loc = String.format("misc-passphrase.txt");
							try (BufferedWriter out = Files.newWriter(new File(loc), Charset.defaultCharset())) {
								out.write(KeyFactory.PEM_PASSPHRASE);
							}
						})
				);
	}

	private HapiApiSpec freezeWorks() {
		return defaultHapiSpec("FreezeWorks")
				.given( ).when(
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

	private HapiApiSpec balancesChangeOnTransfer() {
		return defaultHapiSpec("BalancesChangeOnTransfer")
				.given(
						cryptoCreate("sponsor"),
						cryptoCreate("beneficiary"),
						balanceSnapshot("sponsorBefore", "sponsor"),
						balanceSnapshot("beneficiaryBefore", "beneficiary")
				).when(
						cryptoTransfer(tinyBarsFromTo("sponsor", "beneficiary", 1L))
								.payingWith(GENESIS)
								.memo("Hello World!")
				).then(
						getAccountBalance("sponsor")
								.hasTinyBars(changeFromSnapshot("sponsorBefore", -1L)),
						getAccountBalance("beneficiary")
								.hasTinyBars(changeFromSnapshot("beneficiaryBefore", +1L))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
