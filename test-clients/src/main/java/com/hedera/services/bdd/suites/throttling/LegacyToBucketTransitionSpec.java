package com.hedera.services.bdd.suites.throttling;

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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.SuiteRunner;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.ethereum.net.swarm.Util;
import org.junit.Assert;
import org.junit.runners.Suite;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getReceipt;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateToNewThrottlePropsFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

public class LegacyToBucketTransitionSpec extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(LegacyToBucketTransitionSpec.class);

	/* This bucket capacity is also hard-coded into the resource files used by this test named
	   _test-clients/src/main/resource/ciSpecBucketThrottles-*NodeNetwork.properties_. */
	private static int receiptCapacity = 20;

	public static void main(String... args) {
		new LegacyToBucketTransitionSpec().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[]{
						legacyThrottleConfigStillWorks(),
						overrideConfigTakesPrecedence(),
				}
		);
	}

	private HapiApiSpec overrideConfigTakesPrecedence() {
		AtomicReference<ByteString> legacyProps = new AtomicReference<>();

		return defaultHapiSpec("OverrideConfigTakesPrecedence")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							legacyProps.set(contents);
						}),
						cryptoCreate("civilian").balance(10_000_000_000L),
						updateToNewThrottlePropsFrom(propertiesLocFor(SuiteRunner.expectedNetworkSize))
				).when(
						/* Default transaction bucket. */
						cryptoCreate("ok")
								.payingWith("civilian")
								.via("someTxn")
								.deferStatusResolution(),
						cryptoCreate("NOPE")
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						/* Overflow bucket with custom capacity requirement. */
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith("civilian")
								.deferStatusResolution(),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith("civilian")
								.deferStatusResolution(),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						/* Custom bucket burst period (not a useful property anymore?) */
						getFileInfo(APP_PROPERTIES)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(BUSY),
						UtilVerbs.sleepFor(1_000L),
						sanityCheckReceiptThrottling()
				).then(
						// cleanup:
						fileUpdate(APP_PROPERTIES).contents(ignore -> legacyProps.get())
				);
	}

	private String propertiesLocFor(int numNodes) {
		return String.format("src/main/resource/ciSpecBucketThrottles-%dNodeNetwork.properties", numNodes);
	}

	private HapiSpecOperation sanityCheckReceiptThrottling() {
		return withOpContext((spec, opLog) -> {
			int numBusy = 0;
			int numReceiptsToGet = 3 * receiptCapacity;
			var subOps = IntStream.range(0, numReceiptsToGet)
					.mapToObj(ignore -> getReceipt("someTxn").hasAnswerOnlyPrecheckFrom(BUSY, OK))
					.toArray(HapiSpecOperation[]::new);
			var master = inParallel(subOps);
			allRunFor(spec, master);
			for (int i = 0; i < numReceiptsToGet; i++) {
				var op = (HapiGetReceipt)subOps[i];
				if (op.getResponse().getTransactionGetReceipt().getReceipt().getAccountID().getAccountNum() == 0) {
					numBusy++;
				}
			}
			Assert.assertTrue("> 0 TransactionGetReceipt should be throttled!", numBusy > 0);
		});
	}

	private HapiApiSpec legacyThrottleConfigStillWorks() {
		AtomicReference<ByteString> initialProps = new AtomicReference<>();

		return defaultHapiSpec("LegacyThrottleConfigStillWorks")
				.given(
						withOpContext((spec, opLog) -> {
							var lookup = getFileContents(APP_PROPERTIES);
							allRunFor(spec, lookup);
							var contents = lookup.getResponse().getFileGetContents().getFileContents().getContents();
							initialProps.set(contents);
						}),
						cryptoCreate("civilian").balance(10_000_000_000L),
						fileUpdate(APP_PROPERTIES).overridingProps(Map.of(
								"throttlingTps", "1",
								"simpletransferTps", "1",
								"getReceiptTps", "" + receiptCapacity,
								"queriesTps", "1" ))
				).when(
						/* Transaction throttle. */
						cryptoCreate("ok")
								.payingWith("civilian")
								.via("someTxn").deferStatusResolution(),
						cryptoCreate("NOPE")
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						/* Transfer throttle. */
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith("civilian")
								.deferStatusResolution(),
						cryptoTransfer(tinyBarsFromTo(GENESIS, FUNDING, 1))
								.payingWith("civilian")
								.hasPrecheck(BUSY),
						/* Query throttle. */
						getFileInfo(APP_PROPERTIES)
								.payingWith("civilian")
								.hasAnswerOnlyPrecheck(BUSY),
						UtilVerbs.sleepFor(1_000L),
						sanityCheckReceiptThrottling()
				).then(
						// cleanup:
						fileUpdate(APP_PROPERTIES).contents(ignore -> initialProps.get())
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
