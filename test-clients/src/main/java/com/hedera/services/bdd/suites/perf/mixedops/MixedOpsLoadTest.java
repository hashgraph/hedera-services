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
package com.hedera.services.bdd.suites.perf.mixedops;

import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.infrastructure.OpProvider.STANDARD_PERMISSIBLE_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.inParallel;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.scheduleOpsEnablement;
import static com.hedera.services.bdd.suites.perf.PerfUtilOps.tokenOpsEnablement;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOPIC_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_EXPIRED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import com.hedera.services.bdd.suites.perf.PerfTestLoadSettings;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MixedOpsLoadTest extends LoadTest {
    private static final Logger log = LogManager.getLogger(MixedOpsLoadTest.class);
    private static final int NUM_SUBMISSIONS = 100;
    private final ResponseCodeEnum[] permissiblePrechecks =
            new ResponseCodeEnum[] {
                BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED, UNKNOWN
            };

    private static String sender = "sender";
    private static String receiver = "receiver";
    private static String submitKey = "submitKey";
    private static String topic = "topic";
    private static String token = "token";
    private static String schedule = "schedule";
    private static int messageSize = 1024;

    public static void main(String... args) {
        parseArgs(args);
        MixedOpsLoadTest suite = new MixedOpsLoadTest();
        suite.runSuiteSync();
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(runMixedOps());
    }

    protected HapiSpec runMixedOps() {
        PerfTestLoadSettings settings = new PerfTestLoadSettings();
        Random r = new Random();
        AtomicInteger tokenId = new AtomicInteger(0);
        AtomicInteger scheduleId = new AtomicInteger(0);

        Supplier<HapiSpecOperation[]> mixedOpsBurst =
                () ->
                        new HapiSpecOperation[] {
                            cryptoTransfer(tinyBarsFromTo(sender, receiver, 1L))
                                    .noLogging()
                                    .payingWith(sender)
                                    .signedBy(GENESIS)
                                    .suppressStats(true)
                                    .fee(ONE_HBAR)
                                    .hasKnownStatusFrom(
                                            SUCCESS,
                                            OK,
                                            INSUFFICIENT_PAYER_BALANCE,
                                            UNKNOWN,
                                            TRANSACTION_EXPIRED)
                                    .hasRetryPrecheckFrom(
                                            BUSY,
                                            DUPLICATE_TRANSACTION,
                                            PLATFORM_TRANSACTION_NOT_CREATED,
                                            PAYER_ACCOUNT_NOT_FOUND)
                                    .deferStatusResolution(),
                            submitMessageTo(topic)
                                    .message(
                                            ArrayUtils.addAll(
                                                    ByteBuffer.allocate(8)
                                                            .putLong(Instant.now().toEpochMilli())
                                                            .array(),
                                                    randomUtf8Bytes(messageSize - 8)))
                                    .noLogging()
                                    .payingWith(GENESIS)
                                    .signedBy(sender, submitKey)
                                    .fee(ONE_HBAR)
                                    .suppressStats(true)
                                    .hasRetryPrecheckFrom(
                                            BUSY,
                                            DUPLICATE_TRANSACTION,
                                            PLATFORM_TRANSACTION_NOT_CREATED,
                                            TOPIC_EXPIRED,
                                            INVALID_TOPIC_ID,
                                            INSUFFICIENT_PAYER_BALANCE)
                                    .hasKnownStatusFrom(
                                            SUCCESS,
                                            OK,
                                            INVALID_TOPIC_ID,
                                            INSUFFICIENT_PAYER_BALANCE,
                                            UNKNOWN,
                                            TRANSACTION_EXPIRED)
                                    .deferStatusResolution(),
                            r.nextInt(100) > 5
                                    ? cryptoTransfer(
                                                    moving(1, token + r.nextInt(NUM_SUBMISSIONS))
                                                            .between(sender, receiver))
                                            .payingWith(sender)
                                            .signedBy(GENESIS)
                                            .fee(ONE_HUNDRED_HBARS)
                                            .noLogging()
                                            .suppressStats(true)
                                            .hasPrecheckFrom(
                                                    OK,
                                                    INSUFFICIENT_PAYER_BALANCE,
                                                    EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS,
                                                    DUPLICATE_TRANSACTION)
                                            .hasRetryPrecheckFrom(permissiblePrechecks)
                                            .hasKnownStatusFrom(
                                                    SUCCESS,
                                                    OK,
                                                    INSUFFICIENT_TOKEN_BALANCE,
                                                    TRANSACTION_EXPIRED,
                                                    INVALID_TOKEN_ID,
                                                    UNKNOWN,
                                                    TOKEN_NOT_ASSOCIATED_TO_ACCOUNT)
                                            .deferStatusResolution()
                                    : scheduleSign(
                                                    schedule
                                                            + "-"
                                                            + getHostName()
                                                            + "-"
                                                            + r.nextInt(NUM_SUBMISSIONS))
                                            .ignoreIfMissing()
                                            .noLogging()
                                            .alsoSigningWith(receiver)
                                            .hasPrecheckFrom(OK, INVALID_SCHEDULE_ID)
                                            .hasKnownStatusFrom(
                                                    SUCCESS,
                                                    OK,
                                                    TRANSACTION_EXPIRED,
                                                    INVALID_SCHEDULE_ID,
                                                    UNKNOWN,
                                                    SCHEDULE_ALREADY_EXECUTED)
                                            .fee(ONE_HBAR)
                                            .deferStatusResolution()
                        };

        return defaultHapiSpec("RunMixedOps")
                .given(
                        withOpContext(
                                (spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
                        logIt(ignore -> settings.toString()),
                        newKeyNamed("submitKey"),
                        tokenOpsEnablement(),
                        scheduleOpsEnablement(),
                        cryptoCreate("treasury")
                                .hasRetryPrecheckFrom(permissiblePrechecks)
                                .key(GENESIS))
                .when(
                        fileUpdate(APP_PROPERTIES)
                                .payingWith(GENESIS)
                                .overridingProps(
                                        Map.of(
                                                "hapi.throttling.buckets.fastOpBucket.capacity",
                                                "1300000.0",
                                                "hapi.throttling.ops.consensusUpdateTopic.capacityRequired",
                                                "1.0",
                                                "hapi.throttling.ops.consensusGetTopicInfo.capacityRequired",
                                                "1.0",
                                                "hapi.throttling.ops.consensusSubmitMessage.capacityRequired",
                                                "1.0",
                                                "tokens.maxPerAccount",
                                                "10000000")),
                        cryptoCreate(sender)
                                .balance(initialBalance.getAsLong())
                                .withRecharging()
                                .key(GENESIS)
                                .rechargeWindow(3)
                                .hasRetryPrecheckFrom(permissiblePrechecks),
                        cryptoCreate(receiver)
                                .hasRetryPrecheckFrom(permissiblePrechecks)
                                .key(GENESIS),
                        createTopic(topic).submitKeyName("submitKey"),
                        inParallel(
                                IntStream.range(0, NUM_SUBMISSIONS)
                                        .mapToObj(
                                                ignore ->
                                                        tokenCreate(
                                                                        "token"
                                                                                + tokenId
                                                                                        .getAndIncrement())
                                                                .payingWith(GENESIS)
                                                                .signedBy(GENESIS)
                                                                .fee(ONE_HUNDRED_HBARS)
                                                                .initialSupply(ONE_HUNDRED_HBARS)
                                                                .treasury("treasury")
                                                                .hasRetryPrecheckFrom(
                                                                        permissiblePrechecks)
                                                                .hasPrecheckFrom(
                                                                        DUPLICATE_TRANSACTION, OK)
                                                                .deferStatusResolution()
                                                                .noLogging())
                                        .toArray(n -> new HapiSpecOperation[n])),
                        sleepFor(10000),
                        inParallel(
                                IntStream.range(0, NUM_SUBMISSIONS)
                                        .mapToObj(
                                                ignore ->
                                                        scheduleCreate(
                                                                        "schedule-"
                                                                                + getHostName()
                                                                                + "-"
                                                                                + scheduleId
                                                                                        .getAndIncrement(),
                                                                        cryptoTransfer(
                                                                                tinyBarsFromTo(
                                                                                        sender,
                                                                                        receiver,
                                                                                        1)))
                                                                .signedBy(DEFAULT_PAYER)
                                                                .fee(ONE_HUNDRED_HBARS)
                                                                .alsoSigningWith(sender)
                                                                .hasPrecheckFrom(
                                                                        STANDARD_PERMISSIBLE_PRECHECKS)
                                                                .hasAnyKnownStatus()
                                                                .deferStatusResolution()
                                                                .adminKey(DEFAULT_PAYER)
                                                                .noLogging())
                                        .toArray(n -> new HapiSpecOperation[n])),
                        sleepFor(10000),
                        inParallel(
                                IntStream.range(0, NUM_SUBMISSIONS)
                                        .mapToObj(
                                                i ->
                                                        tokenAssociate(sender, "token" + i)
                                                                .payingWith(GENESIS)
                                                                .signedBy(GENESIS)
                                                                .hasRetryPrecheckFrom(
                                                                        permissiblePrechecks)
                                                                .hasPrecheckFrom(
                                                                        DUPLICATE_TRANSACTION, OK)
                                                                .hasKnownStatusFrom(
                                                                        SUCCESS,
                                                                        TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT,
                                                                        INVALID_TOKEN_ID,
                                                                        TRANSACTION_EXPIRED,
                                                                        OK)
                                                                .fee(ONE_HUNDRED_HBARS)
                                                                .suppressStats(true)
                                                                .deferStatusResolution()
                                                                .noLogging())
                                        .toArray(n -> new HapiSpecOperation[n])),
                        sleepFor(10000))
                .then(defaultLoadTest(mixedOpsBurst, settings));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    private String getHostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.info("Error getting host name");
            return "Hostname-Not-Available";
        }
    }
}
