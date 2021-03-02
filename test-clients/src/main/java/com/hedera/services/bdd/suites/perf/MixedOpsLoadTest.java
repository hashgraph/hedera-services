package com.hedera.services.bdd.suites.perf;

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
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

public class MixedOpsLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(MixedOpsLoadTest.class);
	private final String TARGET_ACCOUNT = "accountForMemo";
	private final String TARGET_TOKEN = "tokenForMemo";
	private final String TARGET_TOPIC = "topicForMemo";

	private final ResponseCodeEnum[] permissiblePrechecks = new ResponseCodeEnum[] {
			OK, BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED, UNKNOWN
	};

	public static void main(String... args) {
		parseArgs(args);

		MixedOpsLoadTest suite = new MixedOpsLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				runMixedMemoOps()
		);
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	// perform cryptoTransfer, submitMessage
	protected HapiApiSpec runMixedMemoOps() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		int numScheduledTxns = 10;
		long ONE_YEAR_IN_SECS = 365 * 24 * 60 * 60;

		Supplier<HapiSpecOperation[]> mixedOpsBurst = () -> {
			String sender = "sender";
			String receiver = "receiver";
			String topicId = "topic";
			String submitKey = "submitKey";
			int messageSize = 256;

			return new HapiSpecOperation[] { cryptoTransfer(
					tinyBarsFromTo(sender, receiver, 1L))
					.noLogging()
					.payingWith(sender)
					.signedBy(GENESIS)
					.suppressStats(true)
					.fee(100_000_000L)
					.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED,
							INVALID_SIGNATURE, PAYER_ACCOUNT_NOT_FOUND)
					.deferStatusResolution(),

					submitMessageTo(topicId)
							.message(ArrayUtils.addAll(
									ByteBuffer.allocate(8).putLong(Instant.now().toEpochMilli()).array(),
									randomUtf8Bytes(messageSize - 8)))
							.noLogging()
							.payingWith(sender)
							.signedBy(sender, submitKey)
							.fee(100_000_000)
							.suppressStats(true)
							.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED,
									TOPIC_EXPIRED,
									INVALID_TOPIC_ID,
									INSUFFICIENT_PAYER_BALANCE)
							.hasKnownStatusFrom(SUCCESS, OK, INVALID_TOPIC_ID)
							.deferStatusResolution(),

//					tokenAssociate(accountId, "token")
//							.payingWith(GENESIS)
//							.signedBy(GENESIS)
//							.hasRetryPrecheckFrom(BUSY, PLATFORM_TRANSACTION_NOT_CREATED, DUPLICATE_TRANSACTION,
//									INSUFFICIENT_PAYER_BALANCE)
//							.hasPrecheckFrom(DUPLICATE_TRANSACTION, OK)
//							.hasKnownStatusFrom(SUCCESS, TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
//									TRANSACTION_EXPIRED, TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED, FAIL_INVALID)
//							.fee(A_HUNDRED_HBARS)
//							.noLogging()
//							.suppressStats(true)
//							.deferStatusResolution()

			};
		};

		return defaultHapiSpec("RunMixedOps")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString()),
						tokenOpsEnablement()
				)
				.when(
						fileUpdate(APP_PROPERTIES)
								.payingWith(GENESIS)
								.overridingProps(Map.of("hapi.throttling.buckets.fastOpBucket.capacity", "1300000.0",
										"hapi.throttling.ops.consensusSubmitMessage.capacityRequired", "1.0",
										"ledger.schedule.txExpiryTimeSecs", "" + ONE_YEAR_IN_SECS)),
						sleepFor(5000),
						logIt(ignore -> settings.toString()),
						cryptoCreate("sender")
								.balance(initialBalance.getAsLong())
								.withRecharging()
								.key(GENESIS)
								.rechargeWindow(3)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						cryptoCreate("receiver")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED)
								.key(GENESIS),
						createTopic("topic")
								.submitKeyName("submitKey")
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						tokenCreate("token")
								.payingWith(GENESIS)
								.logged(),
						inParallel(IntStream.range(0, numScheduledTxns).mapToObj(i ->
								scheduleCreate("schedule" + i,
										cryptoTransfer(tinyBarsFromTo("sender", "receiver", 1))
												.signedBy("sender")
								)
										.advertisingCreation()
										.fee(A_HUNDRED_HBARS)
										.signedBy(DEFAULT_PAYER)
										.inheritingScheduledSigs()
										.withEntityMemo("This is the " + i + "th scheduled txn.")
						).toArray(HapiSpecOperation[]::new))
				).then(
						defaultLoadTest(mixedOpsBurst, settings)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
