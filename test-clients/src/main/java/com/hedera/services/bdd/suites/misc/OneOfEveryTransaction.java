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
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.contractCallLocal;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileAppend;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileUndelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class OneOfEveryTransaction extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(OneOfEveryTransaction.class);

	public static void main(String... args) throws Exception {
		new OneOfEveryTransaction().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						doThings(),
				}
		);
	}

	private HapiApiSpec doThings() {
		/* Crypto signing */
		var complex = KeyShape.threshOf(1, KeyShape.listOf(3), KeyShape.threshOf(1, 3));
		/* File signing */
		var complexWacl = KeyShape.listOf(KeyShape.threshOf(2, 4), KeyShape.threshOf(1, 3));
		var secondComplexWacl = KeyShape.listOf(4);
		var revocationDeleteSigs = secondComplexWacl.signedWith(KeyShape.sigs(ON, OFF, OFF, OFF));
		/* Topic signing */
		var complexAdmin = KeyShape.threshOf(1, KeyShape.listOf(2), KeyShape.threshOf(1, 3));
		/* Contract signing */
		var complexContract = KeyShape.listOf(KeyShape.threshOf(2, 3), KeyShape.threshOf(1, 3));

		return defaultHapiSpec("DoThings").given(
				/* Crypto resources */
				newKeyNamed("firstKey").shape(complex),
				newKeyNamed("secondKey"),
				/* File resources */
				newKeyNamed("fileFirstKey").shape(complexWacl),
				newKeyNamed("fileSecondKey").shape(secondComplexWacl),
				/* Topic resources */
				newKeyNamed("topicKey").shape(complexAdmin),
				/* Contract resources */
				newKeyNamed("contractFirstKey").shape(complexContract),
				newKeyNamed("contractSecondKey"),
				uploadInitCode("Multipurpose"),
				/* Network resources */
				fileCreate("misc").lifetime(2_000_000)
		).when(
				/* Crypto txns */
				cryptoCreate("tbd")
						.receiveThreshold(1_000L)
						.balance(1_234L)
						.key("firstKey"),
				getAccountBalance("tbd").logged(),
				cryptoUpdate("tbd")
						.key("secondKey"),
				cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1_234L)),
				cryptoDelete("tbd")
						.via("deleteTxn")
						.transfer(GENESIS),
				/* File txns */
				fileCreate("fileTbd")
						.key("fileFirstKey")
						.contents("abcdefghijklm"),
				fileAppend("fileTbd")
						.content("nopqrstuvwxyz"),
				fileUpdate("fileTbd")
						.wacl("fileSecondKey"),
				getFileInfo("fileTbd"),
				fileDelete("fileTbd")
						.sigControl(ControlForKey.forKey("fileTbd", revocationDeleteSigs)),
				/* Consensus txns */
				createTopic("topicTbd")
						.memo("'Twas brillig, and the slithy toves...")
						.adminKeyName("topicKey")
						.submitKeyShape(KeyShape.SIMPLE),
				submitMessageTo("topicTbd"),
				updateTopic("topicTbd")
						.signedBy(GENESIS, "topicKey")
						.submitKey(EMPTY_KEY),
				submitMessageTo("topicTbd")
						.signedBy(GENESIS),
				deleteTopic("topicTbd"),
				/* Contract txns */
				uploadInitCode("Multipurpose"),
				contractCreate("Multipurpose")
						.adminKey("contractFirstKey")
						.balance(1),
				contractCall("Multipurpose")
						.sending(1L),
				contractCallLocal("Multipurpose", "pick"),
				contractUpdate("Multipurpose")
						.newKey("contractSecondKey"),
				contractDelete("Multipurpose").transferAccount(GENESIS),
				/* Network txns */
				systemFileDelete("misc")
						.payingWith(SYSTEM_DELETE_ADMIN)
						.fee(0L)
						.updatingExpiry(Instant.now().getEpochSecond() + 1_000_000),
				systemFileUndelete("misc")
						.payingWith(SYSTEM_UNDELETE_ADMIN)
						.fee(0L)
		).then(
				/* Nothing fails. */
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
