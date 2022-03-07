package com.hedera.services.bdd.suites.records;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountRecords;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freezeOnly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
/* --------------------------------------------------------------------------- */

public class MigrationValidation extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(MigrationValidation.class);

	public static void main(String... args) {
		new MigrationValidation().runSuiteSync();
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						migrationPreservesExpectedRecords()
				}
		);
	}

	/**
	 * Builds a spec in which...
	 *
	 * @return the spec.
	 */
	private HapiApiSpec migrationPreservesExpectedRecords() {
		final int NUM_ACCOUNTS = 1;
		final int NUM_RECORDS_PER_ACCOUNT = 10;
		final long TRANSFER_AMOUNT = 2L;
		final long WAIT_FOR_NEW_VERSION_TO_START_MS = 270_000L;

		return defaultHapiSpec("MigrationPreservesExpectedRecords")
				.given(flattened(
						bulkCreateLowSendThresholds(NUM_ACCOUNTS),
						doRecordWorthyTransfers(NUM_ACCOUNTS, NUM_RECORDS_PER_ACCOUNT, TRANSFER_AMOUNT)
				)).when(flattened(
						takeRecordSnapshots(NUM_ACCOUNTS),
						freezeOnly().startingIn(60).seconds(),
						sleepFor(WAIT_FOR_NEW_VERSION_TO_START_MS)
				)).then(flattened(
						checkForExpectedRecords(NUM_ACCOUNTS)
				));
	}

	private HapiSpecOperation[] checkForExpectedRecords(int n) {
		return IntStream
				.range(0, n)
				.mapToObj(i -> getAccountRecords(nameOfAccount(i)).checkingAgainst("record-snapshots"))
				.toArray(l -> new HapiSpecOperation[l]);
	}

	private HapiSpecOperation[] takeRecordSnapshots(int n) {
		return IntStream
				.range(0, n)
				.mapToObj(i -> getAccountRecords(nameOfAccount(i)).savingTo("record-snapshots"))
				.toArray(l -> new HapiSpecOperation[l]);
	}

	private HapiSpecOperation[] bulkCreateLowSendThresholds(int n) {
		return IntStream
				.range(0, n)
				.mapToObj(i -> cryptoCreate(nameOfAccount(i)).sendThreshold(1L))
				.toArray(l -> new HapiSpecOperation[l]);
	}

	private String nameOfAccount(int i) {
		return String.format("account-%d", i);
	}

	private HapiSpecOperation[] doRecordWorthyTransfers(int nAccounts, int nTransfers, long amount) {
		return IntStream
				.range(0, nAccounts * nTransfers)
				.mapToObj(i -> cryptoTransfer(tinyBarsFromTo(nameOfAccount(i / nTransfers), FUNDING, amount)))
				.toArray(l -> new HapiSpecOperation[l]);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
