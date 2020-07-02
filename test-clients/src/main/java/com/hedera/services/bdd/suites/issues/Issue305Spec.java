package com.hedera.services.bdd.suites.issues;

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
import static com.hedera.services.bdd.spec.HapiApiSpec.*;

import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

public class Issue305Spec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(Issue305Spec.class);

	public static void main(String... args) {
		new Issue305Spec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return IntStream.range(0, 5).mapToObj(ignore -> createDeleteInSameRoundWorks()).collect(toList());
	}

	private HapiApiSpec createDeleteInSameRoundWorks() {
		AtomicReference<String> nextFileId = new AtomicReference<>();
		return defaultHapiSpec("CreateDeleteInSameRoundWorks")
				.given(
						newKeyNamed("tbdKey").type(KeyFactory.KeyType.LIST),
						fileCreate("marker").via("markerTxn")
				).when(
						withOpContext((spec, opLog) -> {
							var lookup = getTxnRecord("markerTxn");
							allRunFor(spec, lookup);
							var markerFid = lookup.getResponseRecord().getReceipt().getFileID();
							var nextFid = markerFid.toBuilder().setFileNum(markerFid.getFileNum() + 1).build();
							nextFileId.set(HapiPropertySource.asFileString(nextFid));
							opLog.info("Next file will be " + nextFileId.get());
						})
				).then(
						fileCreate("tbd").key("tbdKey").deferStatusResolution(),
						fileDelete(nextFileId::get).signedBy(GENESIS, "tbdKey").logged(),
						getFileInfo(nextFileId::get).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
