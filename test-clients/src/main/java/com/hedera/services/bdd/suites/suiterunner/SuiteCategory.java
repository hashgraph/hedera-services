package com.hedera.services.bdd.suites.suiterunner;

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

public enum SuiteCategory {
	ALL("All"),
	AUTORENEW_SUITES("Autorenew suites"),
	COMPOSE_SUITES("Compose suites"),
	CONSENSUS_SUITES("Consensus suites"),
	CONTRACT_SUITES("Contract suites"),
	CONTRACT_OP_CODES_SUITES("Contract op codes suite"),
	CONTRACT_RECORDS_SUITES("Contract records suite"),
	CRYPTO_SUITES("Crypto suites"),
	FEES_SUITES("Fees suites"),
	FILE_SUITES("File suites"),
	FILE_NEGATIVE_SUITES("File negative suites"),
	FILE_POSITIVE_SUITES("File positive suites"),
	FREEZE_SUITES("Freeze suites"),
	ISSUES_SUITES("Issues suites"),
	META_SUITES("Meta suites"),
	MISC_SUITES("Misc suites"),
	PERF_SUITES("Perf suites"),
	RECONNECT_SUITES("Reconnect suites"),
	RECORDS_SUITES("Record suites"),
	REGRESSION_SUITES("Regression suites"),
	SCHEDULE_SUITES("Schedule suites"),
	STREAMING_SUITES("Streaming suites"),
	THROTTLING_SUITES("Throttling suites"),
	TOKEN_SUITES("Token suites");

	public final String asString;

	SuiteCategory(final String asString) {
		this.asString = asString;
	}
}
