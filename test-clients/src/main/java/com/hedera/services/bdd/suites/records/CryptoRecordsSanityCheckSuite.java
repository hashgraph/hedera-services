package com.hedera.services.bdd.suites.records;

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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;
import java.util.Set;
import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class CryptoRecordsSanityCheckSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(CryptoRecordsSanityCheckSuite.class);

	public static void main(String... args) {
		new CryptoRecordsSanityCheckSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						cryptoCreateRecordSanityChecks(),
						cryptoDeleteRecordSanityChecks(),
						cryptoTransferRecordSanityChecks(),
						cryptoUpdateRecordSanityChecks(),
						insufficientAccountBalanceRecordSanityChecks(),
						invalidPayerSigCryptoTransferRecordSanityChecks(),
				}
		);
	}


	private HapiApiSpec cryptoCreateRecordSanityChecks() {
		return defaultHapiSpec("CryptoCreateRecordSanityChecks")
				.given(
						takeBalanceSnapshots(FUNDING, NODE, GENESIS)
				).when(
						cryptoCreate("test").via("txn")
				).then(
						validateTransferListForBalances("txn", List.of("test", FUNDING, NODE, GENESIS)),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec cryptoDeleteRecordSanityChecks() {
		return defaultHapiSpec("CryptoDeleteRecordSanityChecks")
				.given(flattened(
						cryptoCreate("test"),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS, "test")
				)).when(
						cryptoDelete("test").via("txn")
				).then(
						validateTransferListForBalances(
								"txn",
								List.of(FUNDING, NODE, GENESIS, "test"),
								Set.of("test")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec cryptoTransferRecordSanityChecks() {
		return defaultHapiSpec("CryptoTransferRecordSanityChecks")
				.given(flattened(
						cryptoCreate("a").balance(100_000L),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS, "a")
				)).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, "a", 1_234L)
						).via("txn")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS, "a")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec cryptoUpdateRecordSanityChecks() {
		return defaultHapiSpec("CryptoUpdateRecordSanityChecks")
				.given(flattened(
						cryptoCreate("test"),
						newKeyNamed("newKey").type(KeyFactory.KeyType.SIMPLE),
						takeBalanceSnapshots(FUNDING, NODE, GENESIS, "test")
				)).when(
						cryptoUpdate("test").key("newKey").via("txn").fee(500_000L).payingWith("test")
				).then(
						validateTransferListForBalances("txn", List.of(FUNDING, NODE, GENESIS, "test")),
						validateRecordTransactionFees("txn")
				);
	}

	private HapiApiSpec insufficientAccountBalanceRecordSanityChecks() {
		final long BALANCE = 500_000_000L;
		return defaultHapiSpec("InsufficientAccountBalanceRecordSanityChecks")
				.given(flattened(
						cryptoCreate("payer").balance(BALANCE),
						cryptoCreate("receiver"),
						takeBalanceSnapshots(FUNDING, NODE, "payer", "receiver")
				)).when(
						cryptoTransfer(tinyBarsFromTo("payer", "receiver", BALANCE / 2))
								.payingWith("payer")
								.via("txn1")
								.deferStatusResolution(),
						cryptoTransfer(tinyBarsFromTo("payer", "receiver", BALANCE / 2))
								.payingWith("payer")
								.via("txn2")
								.hasKnownStatus(INSUFFICIENT_ACCOUNT_BALANCE),
						sleepFor(1_000L)

				).then(
						validateTransferListForBalances(
								List.of("txn1", "txn2"),
								List.of(FUNDING, NODE, "payer", "receiver"))
				);
	}

	private HapiApiSpec invalidPayerSigCryptoTransferRecordSanityChecks() {
		final long BALANCE = 10_000_000L;

		return defaultHapiSpec("InvalidPayerSigCryptoTransferSanityChecks")
				.given(
						newKeyNamed("origKey"),
						newKeyNamed("newKey"),
						cryptoCreate("payer")
								.key("origKey")
								.balance(BALANCE),
						cryptoCreate("receiver")
				).when(
						cryptoUpdate("payer")
								.key("newKey")
								.payingWith("payer")
								.fee(BALANCE / 2)
								.via("updateTxn")
								.deferStatusResolution()
				).then(
						cryptoTransfer(tinyBarsFromTo("payer", "receiver", 1_000L))
								.payingWith("payer")
								.via("transferTxn")
								.signedBy("origKey", "receiver")
								.hasKnownStatus(UNKNOWN)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}

