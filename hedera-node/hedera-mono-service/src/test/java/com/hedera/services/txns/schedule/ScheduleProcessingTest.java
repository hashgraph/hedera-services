/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.schedule;

import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_THROTTLE_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.throttling.TimedFunctionalityThrottling;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import java.time.Instant;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleProcessingTest {

    private static Instant consensusTime = Instant.EPOCH;
    private static final ScheduleID scheduleId1 = IdUtils.asSchedule("0.0.133333");
    private static final ScheduleID scheduleId2 = IdUtils.asSchedule("0.0.233333");
    private static final ScheduleID scheduleId3 = IdUtils.asSchedule("0.0.333333");
    private static final ScheduleID scheduleId4 = IdUtils.asSchedule("0.0.433333");
    private static final ScheduleID scheduleId5 = IdUtils.asSchedule("0.0.533333");

    @Mock private SigImpactHistorian sigImpactHistorian;
    @Mock private ScheduleStore store;
    @Mock private ScheduleExecutor scheduleExecutor;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private ScheduleSigsVerifier scheduleSigsVerifier;
    @Mock private TimedFunctionalityThrottling scheduleThrottling;
    @Mock private ScheduleVirtualValue schedule1;
    @Mock private ScheduleVirtualValue schedule2;
    @Mock private ScheduleVirtualValue schedule3;
    @Mock private ScheduleVirtualValue schedule4;
    @Mock private TxnAccessor schedule1Accessor;
    @Mock private TxnAccessor schedule2Accessor;
    @Mock private TxnAccessor schedule3Accessor;
    @Mock private TxnAccessor schedule4Accessor;
    @Mock private ScheduleSecondVirtualValue bySecond;
    @Mock private TxnAccessor accessor;
    @Mock private MerkleScheduledTransactions schedules;

    private ScheduleProcessing subject;

    @BeforeEach
    void setUp() {
        subject =
                new ScheduleProcessing(
                        sigImpactHistorian,
                        store,
                        scheduleExecutor,
                        dynamicProperties,
                        scheduleSigsVerifier,
                        scheduleThrottling,
                        () -> schedules);
    }

    @Test
    void expireWorksAsExpected() {
        var inOrder = Mockito.inOrder(store, sigImpactHistorian);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime))
                .willReturn(
                        ImmutableList.of(scheduleId1, scheduleId2),
                        ImmutableList.of(scheduleId3),
                        ImmutableList.of());

        // when:
        subject.expire(consensusTime);

        // then:
        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());
        inOrder.verify(store).expire(scheduleId2);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId2).longValue());
        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).expire(scheduleId3);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId3).longValue());
        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store, never()).expire(any());
        inOrder.verify(sigImpactHistorian, never()).markEntityChanged(anyLong());

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void expireLimitedToMaxLoopIterations() {
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of(scheduleId1));

        // when:
        subject.expire(consensusTime);

        // then:
        verify(store, times(50)).expire(scheduleId1);
    }

    @Test
    void triggerNextTransactionExpiringAsNeededWorksAsExpected()
            throws InvalidProtocolBufferException {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1);

        given(store.get(scheduleId1)).willReturn(schedule1);

        subject.isFullySigned =
                k -> {
                    assertEquals(k, schedule1);
                    return true;
                };

        given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId1, store, false))
                .willReturn(Pair.of(OK, accessor));

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertEquals(result, accessor);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId1, store, false);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededLimitedToMaxLoopIterations() {
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(1L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(false);

        given(store.nextScheduleToEvaluate(consensusTime))
                .willReturn(
                        scheduleId1,
                        scheduleId2,
                        scheduleId3,
                        scheduleId4,
                        scheduleId5,
                        IdUtils.asSchedule("0.0.113331"),
                        IdUtils.asSchedule("0.0.113332"),
                        IdUtils.asSchedule("0.0.113333"),
                        IdUtils.asSchedule("0.0.113334"),
                        IdUtils.asSchedule("0.0.113335"),
                        IdUtils.asSchedule("0.0.113336"));

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertNull(result);

        verify(store, times(1)).expire(scheduleId1);
        verify(store, times(1)).expire(scheduleId2);
        verify(store, times(1)).expire(scheduleId3);
        verify(store, times(1)).expire(scheduleId4);
        verify(store, times(1)).expire(scheduleId5);
        verify(store, times(1)).expire(IdUtils.asSchedule("0.0.113331"));
        verify(store, times(1)).expire(IdUtils.asSchedule("0.0.113332"));
        verify(store, times(1)).expire(IdUtils.asSchedule("0.0.113333"));
        verify(store, times(1)).expire(IdUtils.asSchedule("0.0.113334"));
        verify(store, times(1)).expire(IdUtils.asSchedule("0.0.113335"));
        verify(store, never()).expire(IdUtils.asSchedule("0.0.113336"));
    }

    @Test
    void triggerNextTransactionExpiringAsNeededOnlyExpireWorksAsExpected() {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1);

        given(store.get(scheduleId1)).willReturn(schedule1);

        subject.isFullySigned =
                k -> {
                    assertEquals(k, schedule1);
                    return true;
                };

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, true);

        // then:

        assertNull(result);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededErrorsOnSameIdTwiceFromExternal() {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1);
        given(accessor.getScheduleRef()).willReturn(scheduleId1);

        // when:
        assertThrows(
                IllegalStateException.class,
                () ->
                        subject.triggerNextTransactionExpiringAsNeeded(
                                consensusTime, accessor, false));

        // then:

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties, never()).schedulingLongTermEnabled();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededWithLongTermDisabledWorksAsExpected() {

        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(false);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1).willReturn(null);

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertNull(result);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededHandlesNotReady()
            throws InvalidProtocolBufferException {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

        given(store.get(scheduleId1)).willReturn(schedule1);
        given(store.get(scheduleId2)).willReturn(schedule2);

        subject.isFullySigned =
                k -> {
                    if (k == schedule1) {
                        return false;
                    }
                    assertEquals(k, schedule2);
                    return true;
                };

        given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false))
                .willReturn(Pair.of(OK, accessor));

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertEquals(result, accessor);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId2);
        inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededHandlesTriggerNotOk()
            throws InvalidProtocolBufferException {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

        given(store.get(scheduleId1)).willReturn(schedule1);
        given(store.get(scheduleId2)).willReturn(schedule2);

        subject.isFullySigned =
                k -> {
                    if (k == schedule1) {
                        return true;
                    }
                    assertEquals(k, schedule2);
                    return true;
                };

        given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId1, store, false))
                .willReturn(Pair.of(INVALID_SCHEDULE_ID, accessor));
        given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false))
                .willReturn(Pair.of(OK, accessor));

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertEquals(result, accessor);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId1, store, false);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId2);
        inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededHandlesErrorProcessing()
            throws InvalidProtocolBufferException {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, scheduleId2);

        given(store.get(scheduleId1)).willReturn(schedule1);
        given(store.get(scheduleId2)).willReturn(schedule2);

        subject.isFullySigned =
                k -> {
                    if (k == schedule1) {
                        throw new IllegalStateException();
                    }
                    assertEquals(k, schedule2);
                    return true;
                };

        given(scheduleExecutor.getTriggeredTxnAccessor(scheduleId2, store, false))
                .willReturn(Pair.of(OK, accessor));

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertEquals(result, accessor);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId2);
        inOrder.verify(scheduleExecutor).getTriggeredTxnAccessor(scheduleId2, store, false);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededErrorsOnSameIdTwiceFromInternal() {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime))
                .willReturn(scheduleId1, scheduleId2, scheduleId3, scheduleId1);

        subject.isFullySigned = k -> false;

        // when:
        assertThrows(
                IllegalStateException.class,
                () -> subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false));

        // then:

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId2);
        inOrder.verify(store).expire(scheduleId2);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId2).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId3);
        inOrder.verify(store).expire(scheduleId3);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId3).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void triggerNextTransactionExpiringAsNeededSkipsOnNextNull() {
        var inOrder =
                Mockito.inOrder(store, sigImpactHistorian, dynamicProperties, scheduleExecutor);
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        given(store.nextSchedulesToExpire(consensusTime)).willReturn(ImmutableList.of());
        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(store.nextScheduleToEvaluate(consensusTime)).willReturn(scheduleId1, null);

        given(store.get(scheduleId1)).willReturn(schedule1);

        subject.isFullySigned =
                k -> {
                    assertEquals(k, schedule1);
                    throw new IllegalStateException();
                };

        // when:
        var result = subject.triggerNextTransactionExpiringAsNeeded(consensusTime, null, false);

        // then:

        assertNull(result);

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties).schedulingLongTermEnabled();
        inOrder.verify(store).get(scheduleId1);
        inOrder.verify(store).expire(scheduleId1);
        inOrder.verify(sigImpactHistorian)
                .markEntityChanged(fromScheduleId(scheduleId1).longValue());

        inOrder.verify(store).nextSchedulesToExpire(consensusTime);
        inOrder.verify(store).nextScheduleToEvaluate(consensusTime);
        inOrder.verify(dynamicProperties, never()).schedulingLongTermEnabled();

        inOrder.verifyNoMoreInteractions();
    }

    @Test
    void checkFutureThrottlesForCreateWorksAsExpected() throws InvalidProtocolBufferException {
        var inOrder = Mockito.inOrder(scheduleThrottling);

        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        given(schedule1.calculatedExpirationTime()).willReturn(RichInstant.fromJava(consensusTime));

        given(store.getBySecond(consensusTime.getEpochSecond())).willReturn(bySecond);

        given(bySecond.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(consensusTime.getEpochSecond(), 0),
                                                LongLists.immutable.of(
                                                        fromScheduleId(scheduleId1).longValue()),
                                        new RichInstant(consensusTime.getEpochSecond(), 2),
                                                LongLists.immutable.of(
                                                        fromScheduleId(scheduleId2).longValue(),
                                                        fromScheduleId(scheduleId3).longValue(),
                                                        fromScheduleId(scheduleId5).longValue()))));

        given(schedule1.calculatedExpirationTime())
                .willReturn(new RichInstant(consensusTime.getEpochSecond(), 0));
        given(schedule2.calculatedExpirationTime())
                .willReturn(new RichInstant(consensusTime.getEpochSecond(), 2));
        given(schedule3.calculatedExpirationTime())
                .willReturn(new RichInstant(consensusTime.getEpochSecond() - 1, 0));
        given(schedule4.calculatedExpirationTime())
                .willReturn(new RichInstant(consensusTime.getEpochSecond(), 1));

        given(store.getNoError(scheduleId1)).willReturn(schedule1);
        given(store.getNoError(scheduleId2)).willReturn(schedule2);
        given(store.getNoError(scheduleId3)).willReturn(schedule3);

        given(scheduleExecutor.getTxnAccessor(scheduleId1, schedule1, false))
                .willReturn(schedule1Accessor);
        given(scheduleExecutor.getTxnAccessor(scheduleId2, schedule2, false))
                .willReturn(schedule2Accessor);
        given(scheduleExecutor.getTxnAccessor(scheduleId4, schedule4, false))
                .willReturn(schedule4Accessor);

        given(
                        scheduleThrottling.shouldThrottleTxn(
                                any(TxnAccessor.class),
                                eq(new RichInstant(consensusTime.getEpochSecond(), 0).toJava())))
                .willReturn(false);
        given(
                        scheduleThrottling.shouldThrottleTxn(
                                any(TxnAccessor.class),
                                eq(new RichInstant(consensusTime.getEpochSecond(), 1).toJava())))
                .willReturn(false);
        given(
                        scheduleThrottling.shouldThrottleTxn(
                                any(TxnAccessor.class),
                                eq(new RichInstant(consensusTime.getEpochSecond(), 2).toJava())))
                .willReturn(true);

        var result = subject.checkFutureThrottlesForCreate(scheduleId4, schedule4);

        assertEquals(SCHEDULE_FUTURE_THROTTLE_EXCEEDED, result);

        inOrder.verify(scheduleThrottling)
                .shouldThrottleTxn(
                        schedule1Accessor,
                        new RichInstant(consensusTime.getEpochSecond(), 0).toJava());
        inOrder.verify(scheduleThrottling)
                .shouldThrottleTxn(
                        schedule4Accessor,
                        new RichInstant(consensusTime.getEpochSecond(), 1).toJava());
        inOrder.verify(scheduleThrottling)
                .shouldThrottleTxn(
                        schedule2Accessor,
                        new RichInstant(consensusTime.getEpochSecond(), 2).toJava());

        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(false);

        result = subject.checkFutureThrottlesForCreate(scheduleId4, schedule4);

        assertEquals(OK, result);

        given(dynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        given(scheduleThrottling.wasLastTxnGasThrottled()).willReturn(true);

        result = subject.checkFutureThrottlesForCreate(scheduleId4, schedule4);

        assertEquals(SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED, result);

        given(
                        scheduleThrottling.shouldThrottleTxn(
                                any(TxnAccessor.class),
                                eq(new RichInstant(consensusTime.getEpochSecond(), 2).toJava())))
                .willReturn(false);

        result = subject.checkFutureThrottlesForCreate(scheduleId4, schedule4);

        assertEquals(OK, result);
    }

    @Test
    void shouldProcessScheduledTransactionsWorksAsExpected() {

        given(schedules.getCurrentMinSecond()).willReturn(consensusTime.getEpochSecond());

        assertTrue(subject.shouldProcessScheduledTransactions(consensusTime.plusSeconds(1)));
        assertFalse(subject.shouldProcessScheduledTransactions(consensusTime));
        assertFalse(subject.shouldProcessScheduledTransactions(consensusTime.minusSeconds(1)));

        assertTrue(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond()).plusSeconds(1)));
        assertFalse(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond())
                                .plusSeconds(1)
                                .minusNanos(1)));
        assertFalse(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond()).plusNanos(1)));
        assertFalse(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond())));
        assertFalse(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond()).minusSeconds(1)));
        assertFalse(
                subject.shouldProcessScheduledTransactions(
                        Instant.ofEpochSecond(consensusTime.getEpochSecond()).minusNanos(1)));
    }

    @Test
    void getMaxProcessingLoopIterationsWorksAsExpected() {
        given(dynamicProperties.schedulingMaxTxnPerSecond()).willReturn(5L);
        assertEquals(50L, subject.getMaxProcessingLoopIterations());
    }
}
