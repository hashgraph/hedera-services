package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Node
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
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.ContractResources;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runLoadTest;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.runWithProvider;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;
import static java.util.concurrent.TimeUnit.MINUTES;

public class QueryOnlyLoadTest extends LoadTest {
	private static final Logger log = LogManager.getLogger(QueryOnlyLoadTest.class);
	private static final Random r = new Random();

	public static void main(String... args) {
		parseArgs(args);
		QueryOnlyLoadTest suite = new QueryOnlyLoadTest();
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {	runQueryLoadTest() });
	}

	@Override
	public boolean hasInterestingStats() {
		return false;
	}

	private HapiApiSpec runQueryLoadTest() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();

		Supplier<HapiSpecOperation[]> mixedQueries = () -> new HapiSpecOperation[] {
//				opGetAcctInfo(settings),
//				opGetAcctBalance(settings),
//				opGetAcctRecords(settings),
//				opGetTokenInfo(settings),
// 			opGetScheduleInfo(settings),
				opGetTopicInfo(settings),
		};

		return defaultHapiSpec("runQueryLoadTest")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						logIt(ignore -> settings.toString())
				).when(
				).then(
						defaultLoadTest(mixedQueries, settings)
				);
	}


	private static HapiSpecOperation opGetAcctBalance(PerfTestLoadSettings settings) {
		String acctToQuery = String.format("0.0.%d",
				settings.getTestTreasureStartAccount() + r.nextInt(settings.getTotalAccounts()));

		return QueryVerbs.getAccountBalance(acctToQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_ACCOUNT_ID, ACCOUNT_DELETED,ACCOUNT_ID_DOES_NOT_EXIST);
	}

	private static HapiSpecOperation opGetAcctInfo(PerfTestLoadSettings settings) {
		String acctToQuery = String.format("0.0.%d",
				settings.getTestTreasureStartAccount() + r.nextInt(settings.getTotalAccounts()));

		return QueryVerbs.getAccountInfo(acctToQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.nodePayment(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_ACCOUNT_ID,
						ACCOUNT_DELETED,ACCOUNT_ID_DOES_NOT_EXIST);
	}

	private static HapiSpecOperation opGetAcctRecords(PerfTestLoadSettings settings) {
		String acctQuery = String.format("0.0.%d",
				settings.getTestTreasureStartAccount() + r.nextInt(settings.getTotalAccounts()));

		return QueryVerbs.getAccountRecords(acctQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.nodePayment(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_ACCOUNT_ID, ACCOUNT_DELETED,ACCOUNT_ID_DOES_NOT_EXIST);
	}

	private static HapiSpecOperation opGetTopicInfo(PerfTestLoadSettings settings) {
		int startingTopicNum = settings.getTestTreasureStartAccount() + settings.getTotalAccounts();
		String topicQuery = String.format("0.0.%d",
				startingTopicNum + r.nextInt(settings.getTotalTopics()));

		return QueryVerbs.getTopicInfo(topicQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.nodePayment(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK, INVALID_TOPIC_ID)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_TOPIC_ID, TOPIC_EXPIRED);
	}

	private static HapiSpecOperation opGetTokenInfo(PerfTestLoadSettings settings) {
		int startingTokenNum = settings.getTestTreasureStartAccount() + settings.getTotalAccounts() + settings.getTotalTopics();
		String tokenQuery = String.format("0.0.%d",
				startingTokenNum + r.nextInt(103)); // need a way to handle these entities' range

		return QueryVerbs.getTokenInfo(tokenQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.nodePayment(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK, INVALID_TOKEN_ID)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_TOKEN_ID, TOKEN_WAS_DELETED);
	}

	private static HapiSpecOperation opGetScheduleInfo(PerfTestLoadSettings settings) {
		int startingScheduleNum = settings.getTestTreasureStartAccount() + settings.getTotalAccounts() + settings.getTotalTopics()
				+ 103 + 1181397; // need some way to handle these entities' ranges
		String scheduleQuery = String.format("0.0.%d",
				startingScheduleNum + r.nextInt(1000));

		return QueryVerbs.getScheduleInfo(scheduleQuery)
				.payingWith(GENESIS)
				.fee(ONE_HUNDRED_HBARS)
				.nodePayment(ONE_HUNDRED_HBARS)
				.noLogging()
				.hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK, INVALID_SCHEDULE_ID, INVALID_SCHEDULE_ACCOUNT_ID)
				.hasCostAnswerPrecheckFrom(OK, INSUFFICIENT_PAYER_BALANCE,INVALID_SCHEDULE_ID,
						SCHEDULE_ALREADY_DELETED, SCHEDULE_ALREADY_EXECUTED, INVALID_SCHEDULE_PAYER_ID,
						INVALID_SCHEDULE_ACCOUNT_ID);  // Need to verify these response codes
	}


	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
