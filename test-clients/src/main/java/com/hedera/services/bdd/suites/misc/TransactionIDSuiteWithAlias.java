/*
 * -
 *  * ‌
 *  * Hedera Services Node
 *  * ​
 *  * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 *  * ​
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *  * ‍
 *
 */

package com.hedera.services.bdd.suites.misc;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromToWithAlias;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

public class TransactionIDSuiteWithAlias extends HapiApiSuite {

	private static final Logger log = LogManager.getLogger(TransactionIDSuiteWithAlias.class);

	public static void main(String... args) {
		TransactionIDSuiteWithAlias suite = new TransactionIDSuiteWithAlias();
		suite.runSuiteAsync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				cryptoWithAliasTxnPayer(),
//				consensusWithAliasAsPayer(),
//				contractsWithAliasAsPayer(),
//				tokensWithAliasAsPayer(),
//				schedulesWithAliasAsPayer()
		});
	}

	private HapiApiSpec cryptoWithAliasTxnPayer() {
		final var alias = "alias";
		final var autoCreation = "autoCreation";
		final var receiver = "receiver";
		return defaultHapiSpec("cryptoCreateWithAliasTxnPayer")
				.given(
						newKeyNamed(alias),
						cryptoCreate(receiver),
						cryptoTransfer(tinyBarsFromToWithAlias(GENESIS, alias, ONE_HUNDRED_HBARS)).via(autoCreation),
						getTxnRecord(autoCreation).hasChildRecordCount(1)
				).when(
						cryptoCreate("test").payingWith(alias).via("createTxn"),
//						cryptoUpdate("test").memo("updated-memo").payingWith(alias).via("updateTxn"),
//						cryptoTransfer(tinyBarsFromToWithAlias("test", alias, 10)).payingWith(alias).via("transferTxn"),
//						getAccountBalance("test").payingWith(alias).via("accountBalance"),
						getAccountInfo("test").payingWith(alias).via("accountInfo")
//						cryptoDelete("test").payingWith(alias).via("deleteTxn")
				).then(
						getTxnRecord("createTxn").hasPayerAliasNum(alias).logged()
//						getTxnRecord("updateTxn").hasPayerAliasNum(alias).logged(),
//						getTxnRecord("transferTxn").hasPayerAliasNum(alias).logged(),
//						getTxnRecord("deleteTxn").hasPayerAliasNum(alias).logged()
				);
	}

	@Override
	public boolean canRunAsync() {
		return true;
	}
}
