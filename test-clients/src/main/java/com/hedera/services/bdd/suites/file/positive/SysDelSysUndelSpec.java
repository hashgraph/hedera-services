package com.hedera.services.bdd.suites.file.positive;

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
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.systemFileUndelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class SysDelSysUndelSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SysDelSysUndelSpec.class);

	byte[] ORIG_FILE = "SOMETHING".getBytes();

	public static void main(String... args) {
		new SysDelSysUndelSpec().runSuiteAsync();
	}

	@Override
	public boolean canRunConcurrent() {
		return true;
	}

	@Override
	public List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
						systemDeleteThenUndeleteRestoresContentsAndExpiry(),
						systemDeleteWithPastExpiryDestroysFile(),
						distinguishesAdminPrivileges(),
				}
		);
	}

	private HapiApiSpec distinguishesAdminPrivileges() {
		final var lifetime = THREE_MONTHS_IN_SECONDS;

		return defaultHapiSpec("DistinguishesAdminPrivileges")
				.given(
						fileCreate("misc")
								.lifetime(lifetime)
								.contents(ORIG_FILE)
				).when(
				).then(
						systemFileDelete("misc")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.hasPrecheck(NOT_SUPPORTED),
						systemFileUndelete("misc")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.hasPrecheck(AUTHORIZATION_FAILED),
						systemFileDelete(ADDRESS_BOOK)
								.payingWith(GENESIS)
								.hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE)
				);
	}

	private HapiApiSpec systemDeleteWithPastExpiryDestroysFile() {
		final var lifetime = THREE_MONTHS_IN_SECONDS;

		return defaultHapiSpec("systemDeleteWithPastExpiryDestroysFile")
				.given(
						fileCreate("misc")
								.lifetime(lifetime)
								.contents(ORIG_FILE)
				).when(
						systemFileDelete("misc")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.updatingExpiry(1L),
						getFileInfo("misc")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(INVALID_FILE_ID)
				).then(
						systemFileUndelete("misc")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.hasKnownStatus(INVALID_FILE_ID)
				);
	}

	private HapiApiSpec systemDeleteThenUndeleteRestoresContentsAndExpiry() {
		var now = Instant.now().getEpochSecond();
		var lifetime = THREE_MONTHS_IN_SECONDS;
		AtomicLong initExpiry = new AtomicLong();

		return defaultHapiSpec("happyPathFlows")
				.given(
						fileCreate("misc")
								.lifetime(lifetime)
								.contents(ORIG_FILE),
						UtilVerbs.withOpContext((spec, opLog) -> {
							initExpiry.set(spec.registry().getTimestamp("misc").getSeconds());
						})
				).when(
						systemFileDelete("misc")
								.payingWith(SYSTEM_DELETE_ADMIN)
								.fee(0L)
								.updatingExpiry(now + lifetime - 1_000),
						getFileInfo("misc")
								.nodePayment(1_234L)
								.hasAnswerOnlyPrecheck(OK)
						        .hasDeleted(true),
						systemFileUndelete("misc")
								.payingWith(SYSTEM_UNDELETE_ADMIN)
								.fee(0L)
				).then(
						getFileContents("misc")
								.hasContents(ignore -> ORIG_FILE),
						getFileInfo("misc")
								.hasExpiry(initExpiry::get)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
