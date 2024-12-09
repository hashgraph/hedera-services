/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.integration;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.hapi.utils.CommonPbjConverters.protoToPbj;
import static com.hedera.node.app.service.schedule.impl.ScheduleStoreUtility.calculateBytesHash;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_ID_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_COUNTS_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_ORDERS_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULED_USAGES_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0570ScheduleSchema.SCHEDULE_ID_BY_EQUALITY_KEY;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_LAST_ASSIGNED_CONSENSUS_TIME;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_STATE_ACCESS;
import static com.hedera.services.bdd.junit.RepeatableReason.NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION;
import static com.hedera.services.bdd.junit.RepeatableReason.THROTTLE_OVERRIDES;
import static com.hedera.services.bdd.junit.TestTags.INTEGRATION;
import static com.hedera.services.bdd.junit.hedera.NodeSelector.byNodeId;
import static com.hedera.services.bdd.junit.hedera.embedded.EmbeddedMode.REPEATABLE;
import static com.hedera.services.bdd.junit.hedera.embedded.RepeatableEmbeddedHedera.DEFAULT_ROUND_DURATION;
import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.keys.ControlForKey.forKey;
import static com.hedera.services.bdd.spec.keys.KeyShape.sigs;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.keys.SigControl.OFF;
import static com.hedera.services.bdd.spec.keys.SigControl.ON;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleSign;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenFeeScheduleUpdate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.token.CustomFeeSpecs.fixedHbarFee;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.movingHbar;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.EmbeddedVerbs.exposeMaxSchedulable;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.assertHgcaaLogDoesNotContain;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockStreamMustIncludePassFrom;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doAdhoc;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfig;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doWithStartupConfigNow;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.exposeSpecSecondTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromMutation;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overriding;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingAllOf;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.overridingThrottles;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.scheduledExecutionResult;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepForSeconds;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcingContextual;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.waitUntilStartOfNextStakingPeriod;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withStatus;
import static com.hedera.services.bdd.suites.HapiSuite.APP_PROPERTIES;
import static com.hedera.services.bdd.suites.HapiSuite.CIVILIAN_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.HapiSuite.FUNDING;
import static com.hedera.services.bdd.suites.HapiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiSuite.NODE_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HBAR;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_MILLION_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.STAKING_REWARD;
import static com.hedera.services.bdd.suites.HapiSuite.SYSTEM_ADMIN;
import static com.hedera.services.bdd.suites.HapiSuite.TOKEN_TREASURY;
import static com.hedera.services.bdd.suites.HapiSuite.flattened;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.CREATE_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.NEW_SENDER_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.RECEIVER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_KEY;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.SENDER_TXN;
import static com.hedera.services.bdd.suites.hip423.LongTermScheduleUtils.triggerSchedule;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MISSING_EXPIRY_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_EXPIRY_IS_BUSY;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterators.spliteratorUnknownSize;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.hapi.node.base.Setting;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduledCounts;
import com.hedera.hapi.node.state.schedule.ScheduledOrder;
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.junit.HapiTestLifecycle;
import com.hedera.services.bdd.junit.LeakyRepeatableHapiTest;
import com.hedera.services.bdd.junit.RepeatableHapiTest;
import com.hedera.services.bdd.junit.TargetEmbeddedMode;
import com.hedera.services.bdd.junit.support.TestLifecycle;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.keys.ControlForKey;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.Key;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestMethodOrder;

@Order(3)
@Tag(INTEGRATION)
@HapiTestLifecycle
@TargetEmbeddedMode(REPEATABLE)
@TestMethodOrder(OrderAnnotation.class)
public class RepeatableHip423Tests {
    private static final long ONE_MINUTE = 60;
    private static final long FORTY_MINUTES = TimeUnit.MINUTES.toSeconds(40);

    @BeforeAll
    static void beforeAll(@NonNull final TestLifecycle testLifecycle) {
        testLifecycle.overrideInClass(Map.of("scheduling.longTermEnabled", "true"));
    }

    /**
     * Tests the ingest throttle limits the total number of transactions that can be scheduled in a single second.
     */
    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.maxTxnPerSec"})
    final Stream<DynamicTest> cannotScheduleTooManyTxnsInOneSecond() {
        final AtomicLong expiry = new AtomicLong();
        final var oddLifetime = 123 * ONE_MINUTE;
        return hapiTest(
                overriding("scheduling.maxTxnPerSec", "2"),
                cryptoCreate(CIVILIAN_PAYER).balance(10 * ONE_HUNDRED_HBARS),
                scheduleCreate("first", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 123L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringIn(oddLifetime),
                // Consensus time advances exactly one second per transaction in repeatable mode
                exposeSpecSecondTo(now -> expiry.set(now + oddLifetime - 1)),
                sourcing(() -> scheduleCreate("second", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 456L)))
                        .waitForExpiry()
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())),
                sourcing(() -> scheduleCreate("third", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 789)))
                        .waitForExpiry()
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())
                        .hasPrecheck(BUSY)),
                purgeExpiringWithin(oddLifetime));
    }

    /**
     * Tests that expiration time must be in the future---but not too far in the future.
     */
    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.maxExpirationFutureSeconds"})
    final Stream<DynamicTest> expiryMustBeValid() {
        final var lastSecond = new AtomicLong();
        return hapiTest(
                overriding("scheduling.maxExpirationFutureSeconds", "" + ONE_MINUTE),
                exposeSpecSecondTo(lastSecond::set),
                sourcing(() -> scheduleCreate("tooSoon", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 12L)))
                        .expiringAt(lastSecond.get())
                        .hasKnownStatus(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME)),
                exposeSpecSecondTo(lastSecond::set),
                sourcing(() -> scheduleCreate("tooLate", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 34L)))
                        .expiringAt(lastSecond.get() + 1 + ONE_MINUTE + 1)
                        .hasKnownStatus(SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE)),
                scheduleCreate("unspecified", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 56L)))
                        .waitForExpiry()
                        .hasPrecheck(MISSING_EXPIRY_TIME));
    }

    /**
     * Tests that the consensus {@link HederaFunctionality#SCHEDULE_CREATE} throttle is
     * enforced by overriding the dev throttles to the more restrictive mainnet throttles and scheduling one more
     * {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC} that is allowed.
     */
    @LeakyRepeatableHapiTest(
            value = {
                NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
                NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION,
                NEEDS_STATE_ACCESS,
                THROTTLE_OVERRIDES
            },
            overrides = {
                "scheduling.whitelist",
            },
            throttles = "testSystemFiles/mainnet-throttles.json")
    final Stream<DynamicTest> throttlingAndExecutionAsExpected() {
        final var expirySecond = new AtomicLong();
        final var maxLifetime = new AtomicLong();
        final var maxSchedulableTopicCreates = new AtomicInteger();
        return hapiTest(
                overriding("scheduling.whitelist", "ConsensusCreateTopic"),
                doWithStartupConfigNow(
                        "scheduling.maxExpirationFutureSeconds",
                        (value, specTime) -> doAdhoc(() -> {
                            maxLifetime.set(Long.parseLong(value));
                            expirySecond.set(specTime.getEpochSecond() + maxLifetime.get());
                        })),
                cryptoCreate(CIVILIAN_PAYER).balance(ONE_MILLION_HBARS),
                exposeMaxSchedulable(ConsensusCreateTopic, maxSchedulableTopicCreates::set),
                // Schedule the maximum number of topic creations allowed
                sourcing(() -> blockingOrder(IntStream.range(0, maxSchedulableTopicCreates.get())
                        .mapToObj(i -> scheduleCreate(
                                        "topic" + i, createTopic("t" + i).topicMemo("m" + i))
                                .expiringAt(expirySecond.get())
                                .payingWith(CIVILIAN_PAYER)
                                .fee(ONE_HUNDRED_HBARS))
                        .toArray(SpecOperation[]::new))),
                // And confirm the next is throttled
                sourcing(() -> scheduleCreate(
                                "throttledTopicCreation", createTopic("NTB").topicMemo("NOPE"))
                        .expiringAt(expirySecond.get())
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(SCHEDULE_EXPIRY_IS_BUSY)),
                sourcingContextual(spec -> purgeExpiringWithin(maxLifetime.get())));
    }

    /**
     * Tests that the consensus {@link HederaFunctionality#SCHEDULE_CREATE} throttle is
     * enforced by overriding the dev throttles to the more restrictive mainnet throttles and scheduling one more
     * {@link HederaFunctionality#CONSENSUS_CREATE_TOPIC} that is allowed.
     */
    @LeakyRepeatableHapiTest(
            value = {
                NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
                NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION,
                NEEDS_STATE_ACCESS,
                THROTTLE_OVERRIDES
            },
            overrides = {
                "scheduling.whitelist",
            },
            throttles = "testSystemFiles/mainnet-throttles-sans-reservations.json")
    final Stream<DynamicTest> throttlingRebuiltForSecondWhenSnapshotsNoLongerMatch() {
        final var expirySecond = new AtomicLong();
        final var maxLifetime = new AtomicLong();
        final var maxSchedulableTopicCreates = new AtomicInteger();
        return hapiTest(
                overriding("scheduling.whitelist", "ConsensusCreateTopic"),
                doWithStartupConfigNow(
                        "scheduling.maxExpirationFutureSeconds",
                        (value, specTime) -> doAdhoc(() -> {
                            maxLifetime.set(Long.parseLong(value));
                            expirySecond.set(specTime.getEpochSecond() + maxLifetime.get());
                        })),
                cryptoCreate(CIVILIAN_PAYER).balance(ONE_MILLION_HBARS),
                exposeMaxSchedulable(ConsensusCreateTopic, maxSchedulableTopicCreates::set),
                // Schedule one fewer than the maximum number of topic creations allowed using
                // the initial throttles without the PriorityReservations bucket
                sourcing(() -> blockingOrder(IntStream.range(0, maxSchedulableTopicCreates.get() - 1)
                        .mapToObj(i -> scheduleCreate(
                                        "topic" + i, createTopic("t" + i).topicMemo("m" + i))
                                .expiringAt(expirySecond.get())
                                .payingWith(CIVILIAN_PAYER)
                                .fee(ONE_HUNDRED_HBARS))
                        .toArray(SpecOperation[]::new))),
                // Now override the throttles to the mainnet throttles with the PriorityReservations bucket
                // (so that the throttle snapshots in state for this second don't match the new throttles)
                overridingThrottles("testSystemFiles/mainnet-throttles.json"),
                // And confirm we can schedule one more
                sourcing(() -> scheduleCreate(
                                "lastTopicCreation", createTopic("oneMore").topicMemo("N-1"))
                        .expiringAt(expirySecond.get())
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HUNDRED_HBARS)),
                // But then the next is throttled
                sourcing(() -> scheduleCreate(
                                "throttledTopicCreation", createTopic("NTB").topicMemo("NOPE"))
                        .expiringAt(expirySecond.get())
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HUNDRED_HBARS)
                        .hasKnownStatus(SCHEDULE_EXPIRY_IS_BUSY)),
                sourcingContextual(spec -> purgeExpiringWithin(maxLifetime.get())));
    }

    /**
     * Tests that execution of scheduled transactions purges the associated state as expected when a single
     * user transaction fully executes multiple seconds. The test uses three scheduled transactions, two of
     * them in one second and the third one in the next second. After sleeping past the expiration time of
     * all three transactions, executes them via a single triggering transaction and validates the schedule
     * state is as expected.
     */
    @RepeatableHapiTest(value = {NEEDS_LAST_ASSIGNED_CONSENSUS_TIME, NEEDS_STATE_ACCESS})
    final Stream<DynamicTest> executionPurgesScheduleStateAsExpectedInSingleUserTransactions() {
        final var lastSecond = new AtomicLong();
        final AtomicReference<ScheduleStateSizes> startingSizes = new AtomicReference<>();
        final AtomicReference<ScheduleStateSizes> currentSizes = new AtomicReference<>();
        return hapiTest(
                viewScheduleStateSizes(startingSizes::set),
                exposeSpecSecondTo(lastSecond::set),
                cryptoCreate("luckyYou").balance(0L),
                // Schedule the three transfers to lucky you
                sourcing(() -> scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("two", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 2L)))
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("three", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 3L)))
                        .expiringAt(lastSecond.get() + ONE_MINUTE + 1)),
                viewScheduleStateSizes(currentSizes::set),
                // Check that schedule state sizes changed as expected
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), 3, 2, 2, 3, 3)),
                // Let all the schedules expire
                sleepFor((ONE_MINUTE + 2) * 1_000),
                // Trigger them all in a single user transaction
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L + 3L),
                viewScheduleStateSizes(currentSizes::set),
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), 0, 0, 0, 0, 0)));
    }

    /**
     * Tests that execution of scheduled transactions purges the associated state as expected when a single
     * user transaction fully executes multiple seconds. The test uses three scheduled transactions, two of
     * them in one second and the third one in the next second. After sleeping past the expiration time of
     * all three transactions, executes them via a single triggering transaction and validates the schedule
     * state is as expected.
     */
    @RepeatableHapiTest(value = {NEEDS_LAST_ASSIGNED_CONSENSUS_TIME, NEEDS_STATE_ACCESS})
    final Stream<DynamicTest> executeImmediateAndDeletedLongTermAreStillPurgedWhenTimePasses() {
        final var lastExecuteImmediateExpiry = new AtomicLong();
        final AtomicReference<ScheduleStateSizes> startingSizes = new AtomicReference<>();
        final AtomicReference<ScheduleStateSizes> currentSizes = new AtomicReference<>();
        return hapiTest(
                exposeSpecSecondTo(lastExecuteImmediateExpiry::set),
                newKeyNamed("adminKey"),
                cryptoCreate("luckyYou").balance(0L),
                viewScheduleStateSizes(startingSizes::set),
                scheduleCreate("first", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry(false),
                scheduleCreate("last", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 2L)))
                        .waitForExpiry(false),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L),
                doingContextual(spec -> lastExecuteImmediateExpiry.set(expiryOf("last", spec))),
                sourcing(() -> scheduleCreate("deleted", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 3L)))
                        .waitForExpiry()
                        .adminKey("adminKey")
                        .expiringAt(lastExecuteImmediateExpiry.get())),
                scheduleDelete("deleted").signedBy(DEFAULT_PAYER, "adminKey"),
                viewScheduleStateSizes(currentSizes::set),
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), 3, 2, 2, 3, 3)),
                sourcingContextual(spec -> sleepForSeconds(
                        lastExecuteImmediateExpiry.get() - spec.consensusTime().getEpochSecond() + 1)),
                // Trigger all three to be purged in a single user transaction
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                viewScheduleStateSizes(currentSizes::set),
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), 0, 0, 0, 0, 0)));
    }

    /**
     * Tests that execution of scheduled transactions purges the associated state as expected when it takes multiple
     * user transactions to fully execute a given second, by artificially restricting to a single executions per user
     * transaction. The test uses three scheduled transactions, two of them in one second and the third one in the
     * next second. After sleeping past the expiration time of all three transactions, executes them in a sequence of
     * three triggering transactions and validates the schedule state is as expected.
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_LAST_ASSIGNED_CONSENSUS_TIME, NEEDS_STATE_ACCESS},
            overrides = {"scheduling.maxExecutionsPerUserTxn"})
    final Stream<DynamicTest> executionPurgesScheduleStateAsExpectedSplitAcrossUserTransactions() {
        final var lastSecond = new AtomicLong();
        final AtomicReference<ProtoBytes> firstScheduleHash = new AtomicReference<>();
        final AtomicReference<ScheduleID> firstScheduleId = new AtomicReference<>();
        final AtomicReference<ScheduleStateSizes> startingSizes = new AtomicReference<>();
        final AtomicReference<ScheduleStateSizes> currentSizes = new AtomicReference<>();
        return hapiTest(
                overriding("scheduling.maxExecutionsPerUserTxn", "1"),
                viewScheduleStateSizes(startingSizes::set),
                exposeSpecSecondTo(lastSecond::set),
                cryptoCreate("luckyYou").balance(0L),
                // Schedule the three transfers to lucky you
                sourcing(() -> scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("two", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 2L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("three", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 3L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE + 1)),
                viewScheduleStateSizes(currentSizes::set),
                // Check that schedule state sizes changed as expected
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), 3, 2, 2, 3, 3)),
                // Let all the schedules expire
                sleepForSeconds(ONE_MINUTE + 2),
                viewScheduleState((byId, counts, usages, orders, byEquality) -> {
                    final var firstExpiry = lastSecond.get() + ONE_MINUTE;
                    final var firstOrder = new ScheduledOrder(firstExpiry, 0);
                    firstScheduleId.set(requireNonNull(orders.get(firstOrder)));
                    final var firstSchedule = requireNonNull(byId.get(firstScheduleId.get()));
                    final var equalityHash = calculateBytesHash(firstSchedule);
                    firstScheduleHash.set(new ProtoBytes(equalityHash));
                    assertNotNull(byEquality.get(firstScheduleHash.get()), "No equality entry for first schedule");
                }),
                // Now execute them one at a time and assert the expected changes to state
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                viewScheduleState((byId, counts, usages, orders, byEquality) -> {
                    final var firstExpiry = lastSecond.get() + ONE_MINUTE;
                    final var firstKey = new TimestampSeconds(firstExpiry);
                    final var firstCounts = requireNonNull(counts.get(firstKey));
                    assertEquals(1, firstCounts.numberProcessed(), "Wrong number processed for first expiry");
                    assertEquals(2, firstCounts.numberScheduled(), "Wrong number scheduled for first expiry");
                    assertNotNull(usages.get(firstKey), "No usage snapshot for first expiry");
                    // The first transaction's information should be fully purged
                    final var firstOrder = new ScheduledOrder(firstExpiry, 0);
                    assertNull(orders.get(firstOrder), "Order not purged for first transaction");
                    assertNull(byId.get(firstScheduleId.get()), "Schedule not purged for first transaction");
                    assertNull(byEquality.get(firstScheduleHash.get()), "Equality not purged for first transaction");
                    // The following second should not have changed yet
                    final var secondKey = new TimestampSeconds(firstExpiry + 1);
                    final var secondCounts = requireNonNull(counts.get(secondKey));
                    assertEquals(0, secondCounts.numberProcessed(), "Wrong number processed for second expiry");
                    assertEquals(1, secondCounts.numberScheduled(), "Wrong number scheduled for second expiry");
                }),
                getAccountBalance("luckyYou").hasTinyBars(1L),
                // The second execution, in a separate user transaction
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                viewScheduleState((byId, counts, usages, orders, byEquality) -> {
                    final var firstExpiry = lastSecond.get() + ONE_MINUTE;
                    final var firstKey = new TimestampSeconds(firstExpiry);
                    // The counts and usages should be expired for this second
                    assertNull(counts.get(firstKey), "Counts not purged for first expiry");
                    assertNull(usages.get(firstKey), "Usages not purged for first expiry");
                    // Nothing should be different about the following second
                    final var secondKey = new TimestampSeconds(firstExpiry + 1);
                    final var secondCounts = requireNonNull(counts.get(secondKey));
                    assertEquals(0, secondCounts.numberProcessed(), "Wrong number processed for second expiry");
                    assertEquals(1, secondCounts.numberScheduled(), "Wrong number scheduled for second expiry");
                }),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L),
                // The third execution, again in a separate user transaction
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                viewScheduleState((byId, counts, usages, orders, byEquality) -> {
                    // Now everything should be purged
                    final var firstExpiry = lastSecond.get() + ONE_MINUTE;
                    final var secondKey = new TimestampSeconds(firstExpiry + 1);
                    assertNull(counts.get(secondKey), "Counts not purged for second expiry");
                    assertNull(usages.get(secondKey), "Usages not purged for second expiry");
                }),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L + 3L));
    }

    /**
     * Tests that a "backlog" of scheduled transactions to execute does not affect detection of stake period
     * boundary crossings.
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_LAST_ASSIGNED_CONSENSUS_TIME, NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION},
            overrides = {"scheduling.maxExecutionsPerUserTxn"})
    final Stream<DynamicTest> lastProcessTimeDoesNotAffectStakePeriodBoundaryCrossingDetection() {
        final var lastSecond = new AtomicLong();
        final var stakePeriodMins = new AtomicLong();
        return hapiTest(
                overriding("scheduling.maxExecutionsPerUserTxn", "1"),
                doWithStartupConfig(
                        "staking.periodMins", value -> doAdhoc(() -> stakePeriodMins.set(Long.parseLong(value)))),
                sourcing(() -> waitUntilStartOfNextStakingPeriod(stakePeriodMins.get())),
                exposeSpecSecondTo(lastSecond::set),
                cryptoCreate("luckyYou").balance(0L),
                // Schedule the three transfers to lucky you
                sourcing(() -> scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + stakePeriodMins.get() * ONE_MINUTE - 1)),
                sourcing(() -> scheduleCreate("two", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 2L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + stakePeriodMins.get() * ONE_MINUTE - 1)),
                sourcing(() -> scheduleCreate("three", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 3L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + stakePeriodMins.get() * ONE_MINUTE - 1)),
                sourcing(() -> waitUntilStartOfNextStakingPeriod(stakePeriodMins.get())),
                // Now execute them one at a time and assert the expected changes to state
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)).via("boundaryCrossing"),
                getAccountBalance("luckyYou").hasTinyBars(1L),
                getTxnRecord("boundaryCrossing").hasChildRecordCount(1),
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 2L)).via("undistinguishedOne"),
                getTxnRecord("undistinguishedOne").hasChildRecordCount(0),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L),
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 2L)).via("undistinguishedTwo"),
                getTxnRecord("undistinguishedTwo").hasChildRecordCount(0),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L + 3L));
    }

    /**
     * Tests that execution of scheduled transactions purges the associated state as expected when multiple
     * user transactions are required due to running out of consensus times. The test uses four scheduled
     * transactions, two of them in one second and two of them a few seconds later. After sleeping past
     * the expiration time of all four transactions, executes them via two triggering transactions, the first
     * of which has available consensus times for three transactions and the second of which has available
     * consensus times for the fourth transaction.
     */
    @LeakyRepeatableHapiTest(
            value = {NEEDS_LAST_ASSIGNED_CONSENSUS_TIME, NEEDS_STATE_ACCESS},
            overrides = {
                "consensus.handle.maxPrecedingRecords",
                "consensus.handle.maxFollowingRecords",
                "scheduling.consTimeSeparationNanos",
            })
    final Stream<DynamicTest> executionPurgesScheduleStateAsWhenRunningOutOfConsensusTimes() {
        final var lastSecond = new AtomicLong();
        final AtomicReference<ScheduleStateSizes> startingSizes = new AtomicReference<>();
        final AtomicReference<ScheduleStateSizes> currentSizes = new AtomicReference<>();
        return hapiTest(
                exposeSpecSecondTo(lastSecond::set),
                cryptoCreate("luckyYou").balance(0L),
                // From time T, the first transfer will be at T+3, the second at T+6, and the third at T+9;
                // so for a T+12 attempt to run out of time, the separating nanos must be no more than 15
                overridingAllOf(Map.of(
                        "consensus.handle.maxPrecedingRecords", "2",
                        "consensus.handle.maxFollowingRecords", "1",
                        "scheduling.consTimeSeparationNanos", "15")),
                // Schedule the four transfers to lucky you
                sourcing(() -> scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("two", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 2L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                sourcing(() -> scheduleCreate("three", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 3L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE + 3)),
                sourcing(() -> scheduleCreate("four", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 4L)))
                        .waitForExpiry()
                        .expiringAt(lastSecond.get() + ONE_MINUTE + 3)),
                // Let all the schedules expire
                sleepFor((ONE_MINUTE + 4) * 1_000),
                viewScheduleStateSizes(startingSizes::set),
                // Trigger as many as possible in a single user transaction
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                // Verify that was only the first three
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L + 3L),
                viewScheduleStateSizes(currentSizes::set),
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), -3, -1, -1, -3, -3)),
                // Then trigger the last one
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)),
                getAccountBalance("luckyYou").hasTinyBars(1L + 2L + 3L + 4L),
                viewScheduleStateSizes(currentSizes::set),
                doAdhoc(() -> currentSizes.get().assertChangesFrom(startingSizes.get(), -4, -2, -2, -4, -4)));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> executionResultsAreStreamedAsExpected() {
        return hapiTest(
                blockStreamMustIncludePassFrom(scheduledExecutionResult("one", withStatus(SUCCESS))),
                blockStreamMustIncludePassFrom(scheduledExecutionResult("two", withStatus(INVALID_SIGNATURE))),
                cryptoCreate("luckyYou").balance(0L),
                cryptoCreate("cautiousYou").balance(0L).receiverSigRequired(true),
                sourcing(
                        () -> scheduleCreate("payerOnly", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                                .waitForExpiry()
                                .expiringIn(ONE_MINUTE)
                                .via("one")),
                sourcing(() -> scheduleCreate(
                                "receiverSigRequired", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "cautiousYou", 2L)))
                        .waitForExpiry()
                        .expiringIn(ONE_MINUTE)
                        .via("two")),
                sleepForSeconds(ONE_MINUTE),
                // Trigger the executions
                cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> changeTokenFeeWhenScheduled() {
        return hapiTest(
                newKeyNamed("feeScheduleKey"),
                cryptoCreate(TOKEN_TREASURY),
                cryptoCreate("feeCollector").balance(0L),
                cryptoCreate("receiver"),
                cryptoCreate("sender"),
                tokenCreate("fungibleToken")
                        .treasury(TOKEN_TREASURY)
                        .initialSupply(1000)
                        .feeScheduleKey("feeScheduleKey"),
                tokenAssociate("receiver", "fungibleToken"),
                tokenAssociate("sender", "fungibleToken"),
                cryptoTransfer(TokenMovement.moving(5, "fungibleToken").between(TOKEN_TREASURY, "sender")),
                scheduleCreate(
                                "schedule",
                                cryptoTransfer(
                                        TokenMovement.moving(5, "fungibleToken").between("sender", "receiver")))
                        .expiringIn(ONE_MINUTE),
                tokenFeeScheduleUpdate("fungibleToken")
                        .payingWith(DEFAULT_PAYER)
                        .signedBy(DEFAULT_PAYER, "feeScheduleKey")
                        .withCustom(fixedHbarFee(1, "feeCollector")),
                scheduleSign("schedule").payingWith("sender"),
                sleepForSeconds(ONE_MINUTE),
                cryptoCreate("trigger"),
                getAccountBalance("receiver").hasTokenBalance("fungibleToken", 5),
                getAccountBalance("feeCollector").hasTinyBars(1));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> scheduleCreateExecutes() {
        return hapiTest(
                cryptoCreate("luckyYou").balance(0L),
                scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry(false)
                        .expiringIn(FORTY_MINUTES)
                        .via("createTxn"),
                getScheduleInfo("one")
                        .isExecuted()
                        .hasWaitForExpiry(false)
                        .hasRelativeExpiry("createTxn", FORTY_MINUTES - 1));
    }

    /**
     * Validates that once the {@code Iterator<ExecutableTxn>} for a consensus second has been exhausted, that
     * iterator is not recreated when handling another transaction in the same consensus second.
     * <p>
     * Accomplishes this by temporarily removing a critical {@link ScheduleService}'s state key, so that
     * {@link ScheduleService#executableTxns(Instant, Instant, StoreFactory)} will throw an unhandled exception when it
     * tries to access the schedule service states.) Then executes another transaction in the same consensus second; and
     * verifies no exception is logged.
     */
    @RepeatableHapiTest({NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION, NEEDS_STATE_ACCESS})
    final Stream<DynamicTest> afterConsensusSecondIteratorIsExhaustedIsNotRecreated() {
        final var halfSecond = Duration.ofMillis(500);
        final AtomicReference<Map<String, Map<String, Object>>> services = new AtomicReference<>();
        return hapiTest(
                cryptoCreate("luckyYou").balance(0L),
                scheduleCreate("one", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, "luckyYou", 1L)))
                        .waitForExpiry(true)
                        .expiringIn(ONE_MINUTE),
                sleepForSeconds(ONE_MINUTE - 1),
                cryptoCreate("trigger"),
                getAccountBalance("luckyYou").hasTinyBars(1),
                doingContextual(spec -> {
                    final var embeddedHedera = spec.repeatableEmbeddedHederaOrThrow();
                    final var state = embeddedHedera.state();
                    // Create a sufficiently deep copy of the current states and remember it
                    final var backupStates = state.getStates().entrySet().stream()
                            .collect(toMap(Map.Entry::getKey, entry ->
                                    (Map<String, Object>) new ConcurrentHashMap<>(entry.getValue())));
                    services.set(backupStates);
                    // And temporarily remove the schedule-by-id state
                    state.removeServiceState(ScheduleService.NAME, SCHEDULES_BY_ID_KEY);
                    // Then ensure the same consensus second is used for the next transaction
                    embeddedHedera.setRoundDuration(halfSecond);
                }),
                cryptoCreate("sameConsSecond"),
                doingContextual(spec -> {
                    final var embeddedHedera = spec.repeatableEmbeddedHederaOrThrow();
                    final var state = embeddedHedera.state();
                    // Repeat this to purge the cached metadata for the schedule service
                    state.removeServiceState(ScheduleService.NAME, SCHEDULES_BY_ID_KEY);
                    state.getStates().putAll(services.get());
                    embeddedHedera.setRoundDuration(DEFAULT_ROUND_DURATION);
                }),
                // Verify the second transaction in the same second did not recreate the iterator
                assertHgcaaLogDoesNotContain(
                        byNodeId(0L), "Unknown k/v state key SCHEDULES_BY_ID", Duration.ofMillis(100L)));
    }

    /**
     * Validates that if a privileged {@link HederaFunctionality#FILE_UPDATE} transaction is scheduled to be executed
     * then later scheduled transactions executions in the same second reflect the new values of any just-overridden
     * properties.
     */
    @RepeatableHapiTest({NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION, NEEDS_STATE_ACCESS})
    final Stream<DynamicTest> scheduledPrivilegedOverridesAreReflectedInSubsequentSchedules() {
        final AtomicReference<Bytes> originalFile121 = new AtomicReference<>();
        final var lastSecond = new AtomicLong();
        return hapiTest(
                blockStreamMustIncludePassFrom(
                        scheduledExecutionResult("creation", withStatus(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED))),
                exposeSpecSecondTo(lastSecond::set),
                getFileContents(APP_PROPERTIES).consumedBy(bytes -> originalFile121.set(Bytes.wrap(bytes))),
                // We cannot schedule an overriding() because it is not a HapiSpecOperation; so instead schedule
                // an FileUpdate to 0.0.121 with the temporary limit on token symbol bytes
                sourcing(() -> scheduleCreate(
                                "transferListLimitOverride",
                                fileUpdate(APP_PROPERTIES)
                                        .contents(withAddedConfig(originalFile121.get(), "ledger.transfers.maxLen", "2")
                                                .toByteArray()))
                        .designatingPayer(GENESIS)
                        .expiringAt(lastSecond.get() + ONE_MINUTE)),
                // Also schedule a token create with symbol length 3 > 2
                sourcing(() -> scheduleCreate(
                                "nowOverLongTransferList",
                                cryptoTransfer(movingHbar(2L).distributing(DEFAULT_PAYER, STAKING_REWARD, NODE_REWARD)))
                        .expiringAt(lastSecond.get() + ONE_MINUTE)
                        .via("creation")),
                // Trigger execution of both schedules
                sleepForSeconds(ONE_MINUTE),
                cryptoCreate("trigger"),
                // Restore the initial 0.0.121 contents manually now
                sourcing(() -> fileUpdate(APP_PROPERTIES)
                        .contents(originalFile121.get().toByteArray())
                        .payingWith(GENESIS)));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> receiverSigRequiredUpdateIsRecognized() {
        var senderShape = threshOf(2, 3);
        var sigOne = senderShape.signedWith(sigs(ON, OFF, OFF));
        var sigTwo = senderShape.signedWith(sigs(OFF, ON, OFF));
        String schedule = "Z";

        return hapiTest(flattened(
                newKeyNamed(SENDER_KEY).shape(senderShape),
                cryptoCreate(SENDER).key(SENDER_KEY).via(SENDER_TXN),
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                        .payingWith(DEFAULT_PAYER)
                        .waitForExpiry()
                        .expiringIn(FORTY_MINUTES)
                        .recordingScheduledTxn()
                        .alsoSigningWith(SENDER)
                        .sigControl(forKey(SENDER_KEY, sigOne))
                        .via(CREATE_TXN),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                cryptoUpdate(RECEIVER).receiverSigRequired(true),
                scheduleSign(schedule).alsoSigningWith(SENDER_KEY).sigControl(forKey(SENDER_KEY, sigTwo)),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                scheduleSign(schedule).alsoSigningWith(RECEIVER),
                getAccountBalance(RECEIVER).hasTinyBars(0),
                getScheduleInfo(schedule)
                        .hasScheduleId(schedule)
                        .hasWaitForExpiry()
                        .isNotExecuted()
                        .isNotDeleted()
                        .hasRelativeExpiry(CREATE_TXN, FORTY_MINUTES - 1)
                        .hasRecordedScheduledTxn(),
                triggerSchedule(schedule, FORTY_MINUTES),
                getAccountBalance(RECEIVER).hasTinyBars(1),
                scheduleSign(schedule)
                        .alsoSigningWith(SENDER_KEY)
                        .sigControl(forKey(SENDER_KEY, sigTwo))
                        .hasKnownStatus(INVALID_SCHEDULE_ID),
                getAccountBalance(RECEIVER).hasTinyBars(1)));
    }

    @RepeatableHapiTest(NEEDS_VIRTUAL_TIME_FOR_FAST_EXECUTION)
    final Stream<DynamicTest> changeInNestedSigningReqsRespected() {
        var senderShape = threshOf(2, threshOf(1, 3), threshOf(1, 3), threshOf(1, 3));
        var sigOne = senderShape.signedWith(sigs(sigs(OFF, OFF, ON), sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF)));
        var firstSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, OFF, OFF)));
        var secondSigThree = senderShape.signedWith(sigs(sigs(OFF, OFF, OFF), sigs(OFF, OFF, OFF), sigs(ON, ON, OFF)));
        String schedule = "Z";

        return hapiTest(flattened(
                newKeyNamed(SENDER_KEY).shape(senderShape),
                keyFromMutation(NEW_SENDER_KEY, SENDER_KEY).changing(this::bumpThirdNestedThresholdSigningReq),
                cryptoCreate(SENDER).key(SENDER_KEY),
                cryptoCreate(RECEIVER).balance(0L),
                scheduleCreate(schedule, cryptoTransfer(tinyBarsFromTo(SENDER, RECEIVER, 1)))
                        .payingWith(DEFAULT_PAYER)
                        .waitForExpiry()
                        .expiringIn(FORTY_MINUTES)
                        .recordingScheduledTxn()
                        .alsoSigningWith(SENDER)
                        .sigControl(ControlForKey.forKey(SENDER_KEY, sigOne))
                        .via(CREATE_TXN),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                cryptoUpdate(SENDER).key(NEW_SENDER_KEY),
                scheduleSign(schedule)
                        .alsoSigningWith(NEW_SENDER_KEY)
                        .sigControl(forKey(NEW_SENDER_KEY, firstSigThree)),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                scheduleSign(schedule)
                        .alsoSigningWith(NEW_SENDER_KEY)
                        .sigControl(forKey(NEW_SENDER_KEY, secondSigThree)),
                getAccountBalance(RECEIVER).hasTinyBars(0L),
                getScheduleInfo(schedule)
                        .hasScheduleId(schedule)
                        .hasWaitForExpiry()
                        .isNotExecuted()
                        .isNotDeleted()
                        .hasRelativeExpiry(CREATE_TXN, FORTY_MINUTES - 1)
                        .hasRecordedScheduledTxn(),
                triggerSchedule(schedule, FORTY_MINUTES),
                getAccountBalance(RECEIVER).hasTinyBars(1L)));
    }

    /**
     * Tests that system accounts are exempt from throttles.
     */
    @LeakyRepeatableHapiTest(
            value = NEEDS_LAST_ASSIGNED_CONSENSUS_TIME,
            overrides = {"scheduling.maxTxnPerSec"})
    final Stream<DynamicTest> systemAccountsExemptFromThrottles() {
        final AtomicLong expiry = new AtomicLong();
        final var oddLifetime = 123 * ONE_MINUTE;
        return hapiTest(
                overriding("scheduling.maxTxnPerSec", "2"),
                cryptoCreate(CIVILIAN_PAYER).balance(10 * ONE_HUNDRED_HBARS),
                scheduleCreate("first", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 123L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringIn(oddLifetime),
                // Consensus time advances exactly one second per transaction in repeatable mode
                exposeSpecSecondTo(now -> expiry.set(now + oddLifetime - 1)),
                sourcing(() -> scheduleCreate("second", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 456L)))
                        .payingWith(CIVILIAN_PAYER)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())),
                // When scheduling with the system account, the throttle should not apply
                sourcing(() -> scheduleCreate("third", cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 789)))
                        .payingWith(SYSTEM_ADMIN)
                        .fee(ONE_HBAR)
                        .expiringAt(expiry.get())),
                purgeExpiringWithin(oddLifetime));
    }

    private record ScheduleStateSizes(
            int schedulesById,
            int scheduledCounts,
            int scheduledUsages,
            int scheduledOrders,
            int scheduleIdByEquality) {
        /**
         * Asserts that the changes from a starting state are as expected.
         * @param startingSizes the starting state sizes
         * @param schedulesById the expected change in the number of schedules by ID
         * @param scheduledCounts the expected change in the number of scheduled counts
         * @param scheduledUsages the expected change in the number of scheduled usages
         * @param scheduledOrders the expected change in the number of scheduled orders
         * @param scheduleIdByEquality the expected change in the number of schedules by equality
         */
        public void assertChangesFrom(
                @NonNull final ScheduleStateSizes startingSizes,
                final int schedulesById,
                final int scheduledCounts,
                final int scheduledUsages,
                final int scheduledOrders,
                final int scheduleIdByEquality) {
            requireNonNull(startingSizes);
            assertEquals(
                    startingSizes.schedulesById + schedulesById, this.schedulesById, "Wrong number of schedules by ID");
            assertEquals(
                    startingSizes.scheduledCounts + scheduledCounts,
                    this.scheduledCounts,
                    "Wrong number of scheduled counts");
            assertEquals(
                    startingSizes.scheduledUsages + scheduledUsages,
                    this.scheduledUsages,
                    "Wrong number of scheduled usages");
            assertEquals(
                    startingSizes.scheduledOrders + scheduledOrders,
                    this.scheduledOrders,
                    "Wrong number of scheduled orders");
            assertEquals(
                    startingSizes.scheduleIdByEquality + scheduleIdByEquality,
                    this.scheduleIdByEquality,
                    "Wrong number of schedules by equality");
        }
    }

    private interface ScheduleStateConsumer {
        void accept(
                @NonNull ReadableKVState<ScheduleID, Schedule> schedulesById,
                @NonNull ReadableKVState<TimestampSeconds, ScheduledCounts> scheduledCounts,
                @NonNull ReadableKVState<TimestampSeconds, ThrottleUsageSnapshots> scheduledUsages,
                @NonNull ReadableKVState<ScheduledOrder, ScheduleID> scheduledOrders,
                @NonNull ReadableKVState<ProtoBytes, ScheduleID> scheduleIdByEquality);
    }

    private static SpecOperation viewScheduleStateSizes(@NonNull final Consumer<ScheduleStateSizes> consumer) {
        return viewScheduleState((byId, counts, usages, orders, byEquality) -> consumer.accept(new ScheduleStateSizes(
                (int) byId.size(), (int) counts.size(), (int) usages.size(), (int) orders.size(), (int)
                        byEquality.size())));
    }

    private static SpecOperation viewScheduleState(@NonNull final ScheduleStateConsumer consumer) {
        return withOpContext((spec, opLog) -> {
            final var state = spec.embeddedStateOrThrow();
            final var readableStates = state.getReadableStates(ScheduleService.NAME);
            consumer.accept(
                    readableStates.get(SCHEDULES_BY_ID_KEY),
                    readableStates.get(SCHEDULED_COUNTS_KEY),
                    readableStates.get(SCHEDULED_USAGES_KEY),
                    readableStates.get(SCHEDULED_ORDERS_KEY),
                    readableStates.get(SCHEDULE_ID_BY_EQUALITY_KEY));
        });
    }

    private static SpecOperation purgeExpiringWithin(final long seconds) {
        return doingContextual(spec -> {
            final var lastExpiry = spec.consensusTime().getEpochSecond() + seconds;
            allRunFor(spec, sleepFor(seconds * 1_000L));
            final WritableKVState<TimestampSeconds, ScheduledCounts> counts = spec.embeddedStateOrThrow()
                    .getWritableStates(ScheduleService.NAME)
                    .get(SCHEDULED_COUNTS_KEY);
            final int numEarlier =
                    (int) StreamSupport.stream(spliteratorUnknownSize(counts.keys(), DISTINCT | NONNULL), false)
                            .filter(k -> k.seconds() <= lastExpiry)
                            .count();
            final var expectedSize = (int) counts.size() - numEarlier;
            for (int i = 0; i < numEarlier; i++) {
                allRunFor(spec, cryptoTransfer(tinyBarsFromTo(DEFAULT_PAYER, FUNDING, 1L)));
                if (counts.size() == expectedSize) {
                    break;
                }
            }
            assertEquals(expectedSize, counts.size(), "Failed to purge all expired seconds");
        });
    }

    /**
     * Returns the calculated expiration second of the given schedule in the given spec.
     * @param schedule the name of the schedule
     * @param spec the spec
     * @return the calculated expiration second of the schedule
     */
    private static long expiryOf(@NonNull final String schedule, @NonNull final HapiSpec spec) {
        requireNonNull(schedule);
        final ReadableKVState<ScheduleID, Schedule> schedules = spec.embeddedStateOrThrow()
                .getReadableStates(ScheduleService.NAME)
                .get(SCHEDULES_BY_ID_KEY);
        return requireNonNull(schedules.get(protoToPbj(spec.registry().getScheduleId(schedule), ScheduleID.class)))
                .calculatedExpirationSecond();
    }

    private Key bumpThirdNestedThresholdSigningReq(Key source) {
        var newKey = source.getThresholdKey().getKeys().getKeys(2).toBuilder();
        newKey.setThresholdKey(newKey.getThresholdKeyBuilder().setThreshold(2));
        var newKeyList = source.getThresholdKey().getKeys().toBuilder().setKeys(2, newKey);
        return source.toBuilder()
                .setThresholdKey(source.getThresholdKey().toBuilder().setKeys(newKeyList))
                .build();
    }

    /**
     * Returns the given config list with an added setting with the given name and value.
     * @param rawConfigList the raw bytes of the config list
     * @param name the name of the setting to add
     * @param value the value of the setting to add
     * @return the updated config list
     */
    private static Bytes withAddedConfig(
            @NonNull final Bytes rawConfigList, @NonNull final String name, @NonNull final String value) {
        try {
            final var configList = ServicesConfigurationList.PROTOBUF.parse(rawConfigList);
            final var updatedConfigList = new ServicesConfigurationList(
                    Stream.concat(configList.nameValue().stream(), Stream.of(new Setting(name, value, Bytes.EMPTY)))
                            .toList());
            return ServicesConfigurationList.PROTOBUF.toBytes(updatedConfigList);
        } catch (ParseException e) {
            throw new IllegalStateException(e);
        }
    }
}
