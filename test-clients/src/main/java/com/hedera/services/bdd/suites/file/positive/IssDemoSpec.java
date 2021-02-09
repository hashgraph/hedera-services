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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static org.junit.Assert.assertEquals;

public class IssDemoSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(IssDemoSpec.class);

	AtomicReference<ByteString> legacyProps = new AtomicReference<>();

	public static void main(String... args) {
		new IssDemoSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
//						triggerDevIss(),
						refreshNodeDetails(),
				}
		);
	}

	private HapiApiSpec refreshNodeDetails() {
		AtomicReference<ByteString> initialContents = new AtomicReference<>();

		return defaultHapiSpec("RefreshNodeDetails").given(
				withOpContext((spec, opLog) -> {
					var lookup = getFileContents(NODE_DETAILS).logged();
					allRunFor(spec, lookup);
					var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
					initialContents.set(contents);
				})
		).when( ).then(
//				TxnVerbs.fileUpdate(NODE_DETAILS).contents(ignore -> initialContents.get())
		);
	}

	private HapiApiSpec triggerDevIss() {

		return defaultHapiSpec("TriggerDevIss").given(
				withOpContext((spec, opLog) -> {
					var lookup = getFileContents(APP_PROPERTIES);
					allRunFor(spec, lookup);
					var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
					legacyProps.set(contents);
				})
		).when( ).then(
				IntStream.range(0, 10).mapToObj(i -> setMaxFileSizeTo1kb()).toArray(HapiSpecOperation[]::new)
		);
	}

	private HapiSpecOperation setMaxFileSizeTo1kb() {
		return UtilVerbs.blockingOrder(
				fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL).overridingProps(Map.of(
						"maxFileSize", "1"
				)),
				UtilVerbs.sleepFor(500L),
				fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL).droppingUnmentioned().overridingProps(Map.of(
						"maxFileSize", "1024"
				)),
				fileUpdate(APP_PROPERTIES).payingWith(ADDRESS_BOOK_CONTROL).contents(ignore -> legacyProps.get())
		);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
