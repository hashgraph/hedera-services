/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.bdd.suites.perf;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
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
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This test needs sequential distribution of entity ids for accounts, topics, tokens, and scheduled
 * transactions in this order. It also needs roughly the starting and ending point of each entity id
 * types.
 *
 * <p>NOTE: Right now, for historical reason, we have good knowledge of the boundaries of account
 * and topic chunks in current saved state file, but the token and schedule transactions are not
 * clear.
 */
public class QueryOnlyLoadTest extends LoadTest {
    private static final Logger log = LogManager.getLogger(QueryOnlyLoadTest.class);
    private static final Random r = new Random();

    private static int startingScheduleNum = 0;
    private static int startingTokenNum = 0;
    private static int startingTopicNum = 0;

    public static void main(String... args) {
        parseArgs(args);
        QueryOnlyLoadTest suite = new QueryOnlyLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {runQueryLoadTest()});
    }

    private HapiSpec runQueryLoadTest() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();

        Supplier<HapiSpecOperation[]> mixedQueries =
                () ->
                        new HapiSpecOperation[] {
                            opGetAcctInfo(settings),
                            opGetTopicInfo(settings),
                            // TODO: need to generate a new state file for queries of tokens and
                            // schedule transactions to
                            //  work satisfactorily
                            (r.nextInt(100) < 10)
                                    ? opGetTokenInfo(settings)
                                    : opGetAcctInfo(settings),
                            (r.nextInt(100) < 5)
                                    ? opGetScheduleInfo(settings)
                                    : opGetTopicInfo(settings)

                            // These statement are commented out for now.They may be enabled later
                            // when we decide
                            // how to enable and combine all types of queries in the perf test.
                            // opGetAcctBalance(settings),
                            // opGetAcctRecords(settings)
                        };

        return defaultHapiSpec("runQueryLoadTest")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()))
                .when()
                .then(defaultLoadTest(mixedQueries, settings));
    }

    private static HapiSpecOperation opGetAcctBalance(PerfTestLoadSettings settings) {
        String acctToQuery =
                String.format(
                        "0.0.%d",
                        settings.getTestTreasureStartAccount()
                                + r.nextInt(settings.getTotalAccounts()));

        return QueryVerbs.getAccountBalance(acctToQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(
                        DUPLICATE_TRANSACTION, OK, PLATFORM_TRANSACTION_NOT_CREATED)
                .hasCostAnswerPrecheckFrom(
                        OK,
                        INSUFFICIENT_PAYER_BALANCE,
                        INVALID_ACCOUNT_ID,
                        ACCOUNT_DELETED,
                        ACCOUNT_ID_DOES_NOT_EXIST);
    }

    private static HapiSpecOperation opGetAcctRecords(PerfTestLoadSettings settings) {
        String acctRecordsQuery =
                String.format(
                        "0.0.%d",
                        settings.getTestTreasureStartAccount()
                                + r.nextInt(settings.getTotalAccounts()));

        return QueryVerbs.getAccountRecords(acctRecordsQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .nodePayment(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(DUPLICATE_TRANSACTION, OK)
                .hasCostAnswerPrecheckFrom(
                        OK,
                        INSUFFICIENT_PAYER_BALANCE,
                        INVALID_ACCOUNT_ID,
                        ACCOUNT_DELETED,
                        ACCOUNT_ID_DOES_NOT_EXIST);
    }

    private static HapiSpecOperation opGetAcctInfo(PerfTestLoadSettings settings) {
        String acctInfoQuery =
                String.format(
                        "0.0.%d",
                        settings.getTestTreasureStartAccount()
                                + r.nextInt(settings.getTotalAccounts()));

        return QueryVerbs.getAccountInfo(acctInfoQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .nodePayment(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(
                        DUPLICATE_TRANSACTION,
                        OK,
                        PLATFORM_TRANSACTION_NOT_CREATED,
                        TRANSACTION_EXPIRED)
                .hasCostAnswerPrecheckFrom(
                        OK,
                        INSUFFICIENT_PAYER_BALANCE,
                        INVALID_ACCOUNT_ID,
                        ACCOUNT_DELETED,
                        ACCOUNT_ID_DOES_NOT_EXIST);
    }

    private static HapiSpecOperation opGetTopicInfo(PerfTestLoadSettings settings) {
        if (startingTopicNum == 0) {
            // This and the two assignments should be thread-safe in this context.
            startingTopicNum = settings.getTestTreasureStartAccount() + settings.getTotalAccounts();
        }
        String topicInfoQuery =
                String.format("0.0.%d", startingTopicNum + r.nextInt(settings.getTotalTopics()));

        return QueryVerbs.getTopicInfo(topicInfoQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .nodePayment(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(
                        DUPLICATE_TRANSACTION,
                        OK,
                        INVALID_TOPIC_ID,
                        PLATFORM_TRANSACTION_NOT_CREATED,
                        TRANSACTION_EXPIRED)
                .hasCostAnswerPrecheckFrom(
                        OK, INSUFFICIENT_PAYER_BALANCE, INVALID_TOPIC_ID, TOPIC_EXPIRED);
    }

    private static HapiSpecOperation opGetTokenInfo(PerfTestLoadSettings settings) {
        if (startingTokenNum == 0) {
            startingTokenNum =
                    settings.getTestTreasureStartAccount()
                            + settings.getTotalAccounts()
                            + settings.getTotalTopics();
        }

        String tokenInfoQuery =
                String.format("0.0.%d", startingTokenNum + r.nextInt(settings.getTotalTokens()));

        return QueryVerbs.getTokenInfo(tokenInfoQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .nodePayment(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(
                        DUPLICATE_TRANSACTION,
                        OK,
                        INVALID_TOKEN_ID,
                        PLATFORM_TRANSACTION_NOT_CREATED,
                        TRANSACTION_EXPIRED)
                .hasCostAnswerPrecheckFrom(
                        OK, INSUFFICIENT_PAYER_BALANCE, INVALID_TOKEN_ID, TOKEN_WAS_DELETED);
    }

    private static HapiSpecOperation opGetScheduleInfo(PerfTestLoadSettings settings) {
        if (startingScheduleNum == 0) {
            startingScheduleNum =
                    settings.getTestTreasureStartAccount()
                            + settings.getTotalAccounts()
                            + settings.getTotalTopics()
                            + settings.getTotalTokens()
                            + settings.getTotalTokenAssociations();
        }
        String scheduleInfoQuery =
                String.format(
                        "0.0.%d", startingScheduleNum + r.nextInt(settings.getTotalScheduled()));

        return QueryVerbs.getScheduleInfo(scheduleInfoQuery)
                .payingWith(GENESIS)
                .fee(ONE_HUNDRED_HBARS)
                .nodePayment(ONE_HUNDRED_HBARS)
                .noLogging()
                .hasAnswerOnlyPrecheckFrom(
                        DUPLICATE_TRANSACTION,
                        OK,
                        INVALID_SCHEDULE_ID,
                        INVALID_SCHEDULE_ACCOUNT_ID,
                        PLATFORM_TRANSACTION_NOT_CREATED,
                        TRANSACTION_EXPIRED)
                .hasCostAnswerPrecheckFrom(
                        OK,
                        INSUFFICIENT_PAYER_BALANCE,
                        INVALID_SCHEDULE_ID,
                        SCHEDULE_ALREADY_DELETED,
                        SCHEDULE_ALREADY_EXECUTED,
                        INVALID_SCHEDULE_PAYER_ID,
                        INVALID_SCHEDULE_ACCOUNT_ID);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
