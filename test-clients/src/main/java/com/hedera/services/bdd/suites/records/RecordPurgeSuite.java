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
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/* --------------------------- SPEC STATIC IMPORTS --------------------------- */
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
/* --------------------------------------------------------------------------- */

public class RecordPurgeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RecordPurgeSuite.class);

	private static final long EPSILON_MS = 1_000L;
	private static final long CACHE_RECORD_TTL_MS = 3_000L;
	private static final long ACCOUNT_RECORD_TTL_MS = 10_000L;

	public static void main(String... args) {
		new RecordPurgeSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						differentExpiriesArePurgedAtDifferentTimes(),
				}
		);
	}

	/**
	 * Builds a spec in which...
	 *
	 * @return the spec.
	 */
	private HapiApiSpec differentExpiriesArePurgedAtDifferentTimes() {
		final long EXPIRY_DELTA_MS = ACCOUNT_RECORD_TTL_MS - CACHE_RECORD_TTL_MS;

		return defaultHapiSpec("DifferentExpiriesArePurgedAtDifferentTimes")
				.given(
						cryptoCreate("target").sendThreshold(1L)
				).when(
						cryptoTransfer(
								tinyBarsFromTo("target", FUNDING, 555L)
						).payingWith("target").via("txn")
				).then(
						getAccountRecords("target").has(inOrder(
								recordWith().txnId("txn")
						)),
						sleepFor(CACHE_RECORD_TTL_MS + EPSILON_MS),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 555L)
						),
						getAccountRecords("target").has(inOrder(
								recordWith().txnId("txn")
						)),
						sleepFor(EXPIRY_DELTA_MS + EPSILON_MS),
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 555L)
						),
						getAccountRecords("target").has(inOrder())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
