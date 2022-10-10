/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.schedule;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hedera.services.state.virtual.schedule.ScheduleVirtualValueTest.scheduleCreateTxnWith;
import static com.hedera.services.utils.EntityNum.fromScheduleId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.virtualmap.VirtualMap;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HederaScheduleStoreTest {
    private static final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);
    private static final String entityMemo = "Some memo here";
    private static final RichInstant schedulingTXValidStart = new RichInstant(123, 456);
    private static final long expectedExpiry = 1_234_567L;
    private static final RichInstant consensusTime = new RichInstant(expectedExpiry - 1, 0);
    private static final int scheduledTxExpiryTimeSecs = 20;
    private static final long schedulingMaxExpirationFutureSeconds = 30;
    private static final boolean waitForExpiry = true;
    private static final Key adminJKey = asKeyUnchecked(SCHEDULE_ADMIN_KT.asJKeyUnchecked());
    private static final Set<HederaFunctionality> whitelist =
            Set.of(
                    HederaFunctionality.CryptoTransfer,
                    HederaFunctionality.CryptoDelete,
                    HederaFunctionality.TokenBurn);

    private static final ScheduleID created = IdUtils.asSchedule("0.0.333333");
    private static final AccountID schedulingAccount = IdUtils.asAccount("0.0.333");
    private static final AccountID payerId = IdUtils.asAccount("0.0.456");
    private static final AccountID anotherPayerId = IdUtils.asAccount("0.0.457");

    private static final String equalityValue = "equalityValue";
    private static final long equalityKey = 1234L;

    private static final EntityId entityPayer = fromGrpcAccountId(payerId);
    private static final EntityId entitySchedulingAccount = fromGrpcAccountId(schedulingAccount);

    private static final TransactionBody parentTxn =
            scheduleCreateTxnWith(
                    adminJKey,
                    entityMemo,
                    entityPayer.toGrpcAccountId(),
                    entitySchedulingAccount.toGrpcAccountId(),
                    schedulingTXValidStart.toGrpc(),
                    new RichInstant(expectedExpiry, 0).toGrpc(),
                    waitForExpiry);

    private EntityIdSource ids;
    private MerkleScheduledTransactions schedules;
    private VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> byId;
    private VirtualMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond;
    private VirtualMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality;
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private HederaLedger hederaLedger;
    private GlobalDynamicProperties globalDynamicProperties;

    private ScheduleVirtualValue schedule;
    private ScheduleVirtualValue anotherSchedule;

    private HederaScheduleStore subject;

    @BeforeEach
    void setup() {
        schedule = mock(ScheduleVirtualValue.class);
        anotherSchedule = mock(ScheduleVirtualValue.class);

        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.adminKey()).willReturn(Optional.of(SCHEDULE_ADMIN_KT.asJKeyUnchecked()));
        given(schedule.payer()).willReturn(fromGrpcAccountId(payerId));
        given(schedule.memo()).willReturn(Optional.of(entityMemo));
        given(schedule.calculatedExpirationTime()).willReturn(new RichInstant(expectedExpiry, 0));
        given(schedule.equalityCheckKey()).willReturn(equalityKey);
        given(schedule.equalityCheckValue()).willReturn(equalityValue);
        given(schedule.asWritable()).willReturn(schedule);

        given(anotherSchedule.payer()).willReturn(fromGrpcAccountId(anotherPayerId));

        ids = mock(EntityIdSource.class);
        given(ids.newScheduleId(schedulingAccount)).willReturn(created);

        hederaLedger = mock(HederaLedger.class);
        globalDynamicProperties = mock(GlobalDynamicProperties.class);
        given(globalDynamicProperties.scheduledTxExpiryTimeSecs())
                .willReturn(scheduledTxExpiryTimeSecs);
        given(globalDynamicProperties.schedulingMaxExpirationFutureSeconds())
                .willReturn(schedulingMaxExpirationFutureSeconds);

        accountsLedger = mock(TransactionalLedger.class);
        given(accountsLedger.exists(payerId)).willReturn(true);
        given(accountsLedger.exists(schedulingAccount)).willReturn(true);
        given(accountsLedger.get(payerId, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(false);

        schedules = mock(MerkleScheduledTransactions.class);
        byId = mock(VirtualMap.class);
        byExpirationSecond = mock(VirtualMap.class);
        byEquality = mock(VirtualMap.class);
        given(schedules.byId()).willReturn(byId);
        given(schedules.byExpirationSecond()).willReturn(byExpirationSecond);
        given(schedules.byEquality()).willReturn(byEquality);
        given(schedules.getCurrentMinSecond()).willReturn(Long.MAX_VALUE);

        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(true);

        subject = new HederaScheduleStore(globalDynamicProperties, ids, () -> schedules);
        subject.setAccountsLedger(accountsLedger);
        subject.setHederaLedger(hederaLedger);
    }

    @Test
    void commitAndRollbackThrowIseIfNoPendingCreation() {
        assertThrows(IllegalStateException.class, subject::commitCreation);
        assertThrows(IllegalStateException.class, subject::rollbackCreation);
    }

    @Test
    void commitPutsToMapAndClears() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        subject.commitCreation();

        verify(byId).put(new EntityNumVirtualKey(fromScheduleId(created)), schedule);

        AtomicReference<ScheduleSecondVirtualValue> secValue = new AtomicReference<>();
        verify(byExpirationSecond)
                .put(
                        eq(new SecondSinceEpocVirtualKey(expectedExpiry)),
                        argThat(a -> secValue.compareAndSet(null, a) || true));

        AtomicReference<ScheduleEqualityVirtualValue> eqValue = new AtomicReference<>();
        verify(byEquality)
                .put(
                        eq(new ScheduleEqualityVirtualKey(equalityKey)),
                        argThat(a -> eqValue.compareAndSet(null, a) || true));

        verify(schedules).setCurrentMinSecond(expectedExpiry);

        assertEquals(1, eqValue.get().getIds().size());
        assertEquals(
                eqValue.get().getIds().get(equalityValue), fromScheduleId(created).longValue());

        assertEquals(1, secValue.get().getIds().size());
        assertEquals(
                secValue.get().getIds().get(new RichInstant(expectedExpiry, 0)),
                LongLists.immutable.of(fromScheduleId(created).longValue()));

        assertSame(HederaScheduleStore.NO_PENDING_ID, subject.pendingId);
        assertNull(subject.pendingCreation);
    }

    @Test
    void commitAddsToMaps() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        var secValue = mock(ScheduleSecondVirtualValue.class);
        var eqValue = mock(ScheduleEqualityVirtualValue.class);
        given(secValue.asWritable()).willReturn(secValue);
        given(eqValue.asWritable()).willReturn(eqValue);

        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(secValue);
        given(byEquality.get(new ScheduleEqualityVirtualKey(equalityKey))).willReturn(eqValue);
        given(schedules.getCurrentMinSecond()).willReturn(Long.MIN_VALUE);

        subject.commitCreation();

        verify(byId).put(new EntityNumVirtualKey(fromScheduleId(created)), schedule);
        verify(byExpirationSecond).put(new SecondSinceEpocVirtualKey(expectedExpiry), secValue);
        verify(byEquality).put(new ScheduleEqualityVirtualKey(equalityKey), eqValue);

        verify(schedules, never()).setCurrentMinSecond(anyLong());

        verify(secValue)
                .add(
                        new RichInstant(expectedExpiry, 0),
                        LongLists.immutable.of(fromScheduleId(created).longValue()));

        verify(eqValue).add(equalityValue, fromScheduleId(created).longValue());

        assertSame(HederaScheduleStore.NO_PENDING_ID, subject.pendingId);
        assertNull(subject.pendingCreation);
    }

    @Test
    void rollbackReclaimsIdAndClears() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        subject.rollbackCreation();

        verify(byId, never()).put(new EntityNumVirtualKey(fromScheduleId(created)), schedule);
        verify(ids).reclaimLastId();

        assertSame(HederaScheduleStore.NO_PENDING_ID, subject.pendingId);
        assertNull(subject.pendingCreation);
    }

    @Test
    void understandsPendingCreation() {
        assertFalse(subject.isCreationPending());

        subject.pendingId = created;

        assertTrue(subject.isCreationPending());
    }

    @Test
    void getThrowsIseOnMissing() {
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> subject.get(created));
    }

    @Test
    void getNoErrorReturnsNullOnMissing() {
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(null);

        assertNull(subject.getNoError(created));
    }

    @Test
    void applicationRejectsMissing() {
        final var change = mock(Consumer.class);

        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));
    }

    @Test
    @SuppressWarnings("unchecked")
    void provisionalApplicationWorks() {
        final var change = mock(Consumer.class);
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        subject.apply(created, change);

        verify(change).accept(schedule);
        verify(schedules, never()).byId();
    }

    @Test
    void applicationWorks() {
        final var change = mock(Consumer.class);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        final var inOrder = Mockito.inOrder(change, byId, schedule);

        subject.apply(created, change);

        inOrder.verify(byId).get(new EntityNumVirtualKey(fromScheduleId(created)));
        inOrder.verify(schedule).asWritable();
        inOrder.verify(change).accept(schedule);
        inOrder.verify(byId).put(new EntityNumVirtualKey(fromScheduleId(created)), schedule);
    }

    @Test
    void applicationAlwaysReplacesModifiableSchedule() {
        final var change = mock(Consumer.class);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        final var inOrder = Mockito.inOrder(change, byId, schedule);

        willThrow(IllegalStateException.class).given(change).accept(any());

        assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));

        inOrder.verify(byId).get(new EntityNumVirtualKey(fromScheduleId(created)));
        inOrder.verify(schedule).asWritable();
        inOrder.verify(change).accept(schedule);
        inOrder.verify(byId).put(new EntityNumVirtualKey(fromScheduleId(created)), schedule);
    }

    @Test
    void createProvisionallyImmediatelyRejectsNonWhitelistedTxn() {
        given(globalDynamicProperties.schedulingWhitelist())
                .willReturn(EnumSet.of(HederaFunctionality.TokenMint));
        final var mockCreation = mock(ScheduleVirtualValue.class);
        given(mockCreation.scheduledFunction()).willReturn(HederaFunctionality.TokenBurn);

        final var outcome = subject.createProvisionally(mockCreation, consensusTime);

        assertEquals(SCHEDULED_TRANSACTION_NOT_IN_WHITELIST, outcome.status());
        assertNull(outcome.created());
    }

    @Test
    void createProvisionallyWorks() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(hederaLedger.usabilityOf(any())).willReturn(OK);

        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);

        final var outcome = subject.createProvisionally(expected, consensusTime);

        assertEquals(OK, outcome.status());
        assertEquals(created, outcome.created());
        assertEquals(created, subject.pendingId);
        assertSame(expected, subject.pendingCreation);
        assertEquals(
                consensusTime.getSeconds() + scheduledTxExpiryTimeSecs,
                expected.calculatedExpirationTime().getSeconds());
        assertTrue(parentTxn.getScheduleCreate().getWaitForExpiry());
        assertFalse(expected.calculatedWaitForExpiry());
    }

    @Test
    void createProvisionallyWorksWithLongTermTxnsEnabled() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        given(hederaLedger.usabilityOf(any())).willReturn(OK);

        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);

        final var outcome = subject.createProvisionally(expected, consensusTime);

        assertEquals(OK, outcome.status());
        assertEquals(created, outcome.created());
        assertEquals(created, subject.pendingId);
        assertSame(expected, subject.pendingCreation);
        assertEquals(expectedExpiry, expected.calculatedExpirationTime().getSeconds());
        assertTrue(parentTxn.getScheduleCreate().getWaitForExpiry());
        assertTrue(expected.calculatedWaitForExpiry());
    }

    @Test
    void createProvisionallyWorksWithLongTermTxnsEnabledNoExpirationProvided() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        given(hederaLedger.usabilityOf(any())).willReturn(OK);

        final var expected =
                ScheduleVirtualValue.from(
                        parentTxn.toBuilder()
                                .setScheduleCreate(
                                        parentTxn.getScheduleCreate().toBuilder()
                                                .clearExpirationTime())
                                .build()
                                .toByteArray(),
                        0L);

        final var outcome = subject.createProvisionally(expected, consensusTime);

        assertEquals(OK, outcome.status());
        assertEquals(created, outcome.created());
        assertEquals(created, subject.pendingId);
        assertSame(expected, subject.pendingCreation);
        assertEquals(
                consensusTime.getSeconds() + scheduledTxExpiryTimeSecs,
                expected.calculatedExpirationTime().getSeconds());
        assertTrue(parentTxn.getScheduleCreate().getWaitForExpiry());
        assertTrue(expected.calculatedWaitForExpiry());
    }

    @Test
    void createProvisionallyWithLongTermTxnsEnabledRejectsNotAboveConsensusTimeSeconds() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        var txn = TransactionBody.newBuilder().mergeFrom(parentTxn);
        txn.getScheduleCreateBuilder().getExpirationTimeBuilder().setNanos(500);

        final var expected = ScheduleVirtualValue.from(txn.build().toByteArray(), 0L);

        assertEquals(500, expected.expirationTimeProvided().getNanos());

        var outcome = subject.createProvisionally(expected, new RichInstant(expectedExpiry, 0));

        assertEquals(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME, outcome.status());
        assertNull(outcome.created());

        outcome = subject.createProvisionally(expected, new RichInstant(expectedExpiry, 600));

        assertEquals(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME, outcome.status());
        assertNull(outcome.created());

        outcome = subject.createProvisionally(expected, new RichInstant(expectedExpiry, 400));

        assertEquals(SCHEDULE_EXPIRATION_TIME_MUST_BE_HIGHER_THAN_CONSENSUS_TIME, outcome.status());
        assertNull(outcome.created());
    }

    @Test
    void createProvisionallyWithLongTermTxnsEnabledRejectsTooFarInFuture() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);

        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);

        final var outcome =
                subject.createProvisionally(
                        expected,
                        RichInstant.fromJava(
                                consensusTime
                                        .toJava()
                                        .minusSeconds(schedulingMaxExpirationFutureSeconds + 1)));

        assertEquals(SCHEDULE_EXPIRATION_TIME_TOO_FAR_IN_FUTURE, outcome.status());
        assertNull(outcome.created());
    }

    @Test
    void createProvisionallyRejectsInvalidPayer() {
        final var parentTxn =
                scheduleCreateTxnWith(
                        adminJKey,
                        entityMemo,
                        IdUtils.asAccount("22.33.44"),
                        entitySchedulingAccount.toGrpcAccountId(),
                        schedulingTXValidStart.toGrpc());
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

        final var outcome =
                subject.createProvisionally(
                        ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L), consensusTime);

        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.status());
        assertNull(outcome.created());
    }

    @Test
    void getCanReturnPending() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        assertSame(schedule, subject.get(created));
    }

    @Test
    void getNoErrorCanReturnPending() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        assertSame(schedule, subject.getNoError(created));
    }

    @Test
    void existsCanHandlePending() {
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        assertTrue(subject.exists(created));
    }

    @Test
    void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        assertFalse(subject.exists(HederaScheduleStore.NO_PENDING_ID));
    }

    @Test
    void createProvisionallyRejectsInvalidScheduler() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        final var invalidId = IdUtils.asAccount("22.33.44");
        given(hederaLedger.usabilityOf(entityPayer.toGrpcAccountId())).willReturn(OK);
        given(hederaLedger.usabilityOf(invalidId)).willReturn(INVALID_ACCOUNT_ID);

        final var differentParentTxn =
                scheduleCreateTxnWith(
                        adminJKey,
                        entityMemo,
                        entityPayer.toGrpcAccountId(),
                        invalidId,
                        schedulingTXValidStart.toGrpc());

        rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, differentParentTxn);
    }

    @Test
    void rejectsCreateProvisionallyDeletedPayer() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(hederaLedger.isDeleted(payerId)).willReturn(true);

        rejectWith(INVALID_SCHEDULE_PAYER_ID, parentTxn);
    }

    @Test
    void rejectsCreateProvisionallyDeletedScheduler() {
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);
        given(hederaLedger.usabilityOf(payerId)).willReturn(OK);
        given(hederaLedger.usabilityOf(schedulingAccount)).willReturn(ACCOUNT_DELETED);

        rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, parentTxn);
    }

    @Test
    void rejectsCreateProvisionallyWithMissingSchedulingAccount() {
        given(hederaLedger.usabilityOf(payerId)).willReturn(OK);
        given(hederaLedger.usabilityOf(schedulingAccount)).willReturn(INVALID_ACCOUNT_ID);
        given(globalDynamicProperties.schedulingWhitelist()).willReturn(whitelist);

        rejectWith(INVALID_SCHEDULE_ACCOUNT_ID, parentTxn);
    }

    @Test
    void recognizesCollidingSchedule() {
        final var candSchedule = ScheduleVirtualValue.from(parentTxn.toByteArray(), expectedExpiry);
        final var eqValue = new ScheduleEqualityVirtualValue();
        eqValue.add(candSchedule.equalityCheckValue(), fromScheduleId(created).longValue());

        given(byEquality.get(new ScheduleEqualityVirtualKey(candSchedule.equalityCheckKey())))
                .willReturn(eqValue);

        final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

        assertEquals(Pair.of(created, schedule), scheduleIdPair);
    }

    @Test
    void understandsMissingButExistsInEqMap() {
        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), expectedExpiry);
        final var eqValue = new ScheduleEqualityVirtualValue();

        given(byEquality.get(new ScheduleEqualityVirtualKey(expected.equalityCheckKey())))
                .willReturn(eqValue);

        final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

        assertNull(scheduleIdPair.getLeft());
        assertEquals(expected, scheduleIdPair.getRight());
    }

    @Test
    void understandsMissingExistsInEqMapButNoExists() {
        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), expectedExpiry);
        final var eqValue = new ScheduleEqualityVirtualValue();
        eqValue.add(
                expected.equalityCheckValue(),
                fromScheduleId(IdUtils.asSchedule("0.0.123333")).longValue());

        given(byEquality.get(new ScheduleEqualityVirtualKey(expected.equalityCheckKey())))
                .willReturn(eqValue);

        final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

        assertNull(scheduleIdPair.getLeft());
        assertEquals(expected, scheduleIdPair.getRight());
    }

    @Test
    void recognizesCollisionWithPending() {
        final var candSchedule = ScheduleVirtualValue.from(parentTxn.toByteArray(), expectedExpiry);
        subject.pendingCreation = candSchedule;
        subject.pendingId = created;

        final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

        assertEquals(Pair.of(created, candSchedule), scheduleIdPair);
    }

    @Test
    void understandsMissing() {
        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);

        final var scheduleIdPair = subject.lookupSchedule(parentTxn.toByteArray());

        assertNull(scheduleIdPair.getLeft());
        assertEquals(expected, scheduleIdPair.getRight());
    }

    @Test
    void deletesAsExpected() {
        final var now = Instant.ofEpochSecond(expectedExpiry - 1);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);

        final var outcome = subject.deleteAt(created, now);

        verify(byId, never()).remove(any());
        verify(schedule).markDeleted(now);
        assertEquals(OK, outcome);
    }

    @Test
    void rejectsDeletionMissingAdminKey() {
        final var now = Instant.ofEpochSecond(expectedExpiry - 1);
        given(schedule.adminKey()).willReturn(Optional.empty());

        final var outcome = subject.deleteAt(created, now);

        verify(byId, never()).remove(any());
        verify(schedule, never()).markDeleted(now);
        assertEquals(SCHEDULE_IS_IMMUTABLE, outcome);
    }

    @Test
    void rejectsDeletionAlreadyDeleted() {
        given(schedule.isDeleted()).willReturn(true);

        final var outcome = subject.deleteAt(created, schedulingTXValidStart.toJava());

        verify(schedule, never()).markDeleted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
    }

    @Test
    void rejectsDeletionExpirationPassedLongTermTxnDisabled() {
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(false);
        final var now = Instant.ofEpochSecond(expectedExpiry + 1);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);

        final var outcome = subject.deleteAt(created, now);

        verify(byId, never()).remove(any());
        verify(schedule, never()).markDeleted(any());
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    void allowsDeletionExpirationFutureLongTermTxnEnabled() {
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        final var now = Instant.ofEpochSecond(expectedExpiry - 1);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);

        final var outcome = subject.deleteAt(created, now);

        verify(byId, never()).remove(any());
        verify(schedule).markDeleted(now);
        assertEquals(OK, outcome);
    }

    @Test
    void rejectsDeletionExpirationPassed() {
        given(globalDynamicProperties.schedulingLongTermEnabled()).willReturn(true);
        final var outcome = subject.deleteAt(created, Instant.ofEpochSecond(expectedExpiry + 1));

        verify(schedule, never()).markDeleted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_PENDING_EXPIRATION, outcome);
    }

    @Test
    void rejectsDeletionMissingSchedule() {
        final var now = schedulingTXValidStart.toJava();
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        final var outcome = subject.deleteAt(created, now);

        verify(byId, never()).remove(any());
        verify(schedule, never()).markDeleted(any());
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    void rejectsExecutionWhenDeleted() {
        given(schedule.isDeleted()).willReturn(true);

        final var outcome = subject.markAsExecuted(created, consensusNow);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
    }

    @Test
    void rejectsExecutionWhenExecuted() {
        given(schedule.isExecuted()).willReturn(true);

        final var outcome = subject.markAsExecuted(created, consensusNow);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_ALREADY_EXECUTED, outcome);
    }

    @Test
    void rejectsExecutionMissingSchedule() {
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        final var outcome = subject.markAsExecuted(created, consensusNow);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    void marksExecutedAsExpected() {
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);

        subject.markAsExecuted(created, consensusNow);

        verify(schedule).markExecuted(consensusNow);
        verify(byId, never()).remove(any());
    }

    @Test
    void rejectsPreMarkExecutionWhenDeleted() {
        given(schedule.isDeleted()).willReturn(true);

        final var outcome = subject.preMarkAsExecuted(created);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_ALREADY_DELETED, outcome);
    }

    @Test
    void rejectsPreMarkExecutionWhenExecuted() {
        given(schedule.isExecuted()).willReturn(true);

        final var outcome = subject.preMarkAsExecuted(created);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(SCHEDULE_ALREADY_EXECUTED, outcome);
    }

    @Test
    void rejectsPreMarkExecutionMissingSchedule() {
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        final var outcome = subject.preMarkAsExecuted(created);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    void preMarkExecutedAsExpected() {
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);

        subject.preMarkAsExecuted(created);

        verify(schedule, never()).markExecuted(any());
        verify(byId, never()).remove(any());
    }

    @Test
    void expiresAsExpected() {
        var entityKey = new EntityNumVirtualKey(fromScheduleId(created));
        given(byId.remove(entityKey)).willReturn(schedule);
        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(bySecondValue.asWritable()).willReturn(bySecondValue);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);
        given(bySecondValue.getIds()).willReturn(Collections.emptyNavigableMap());

        var byEqualityValue = mock(ScheduleEqualityVirtualValue.class);
        given(byEqualityValue.asWritable()).willReturn(byEqualityValue);
        given(byEquality.get(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey())))
                .willReturn(byEqualityValue);
        given(byEqualityValue.getIds()).willReturn(Collections.emptySortedMap());

        subject.expire(created);

        verify(byId).remove(new EntityNumVirtualKey(fromScheduleId(created)));

        verify(byExpirationSecond).get(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(bySecondValue).asWritable();
        verify(byExpirationSecond).remove(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(bySecondValue)
                .removeId(schedule.calculatedExpirationTime(), entityKey.getKeyAsLong());

        verify(byEquality).get(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey()));
        verify(byEqualityValue).asWritable();
        verify(byEquality).remove(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey()));
        verify(byEqualityValue).remove(schedule.equalityCheckValue(), entityKey.getKeyAsLong());
    }

    @Test
    void expireDoesntRemoveNonEmptyValues() {
        var entityKey = new EntityNumVirtualKey(fromScheduleId(created));
        given(byId.remove(entityKey)).willReturn(schedule);
        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(bySecondValue.asWritable()).willReturn(bySecondValue);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);
        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                Collections.singletonMap(
                                        consensusTime, LongLists.immutable.empty())));

        var byEqualityValue = mock(ScheduleEqualityVirtualValue.class);
        given(byEqualityValue.asWritable()).willReturn(byEqualityValue);
        given(byEquality.get(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey())))
                .willReturn(byEqualityValue);
        given(byEqualityValue.getIds()).willReturn(ImmutableSortedMap.of("foo", 1L));

        subject.expire(created);

        verify(byId).remove(new EntityNumVirtualKey(fromScheduleId(created)));

        verify(byExpirationSecond).get(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(bySecondValue).asWritable();
        verify(byExpirationSecond)
                .put(new SecondSinceEpocVirtualKey(expectedExpiry), bySecondValue);
        verify(byExpirationSecond, never()).remove(any());
        verify(bySecondValue)
                .removeId(schedule.calculatedExpirationTime(), entityKey.getKeyAsLong());

        verify(byEquality).get(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey()));
        verify(byEqualityValue).asWritable();
        verify(byEquality)
                .put(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey()), byEqualityValue);
        verify(byEquality, never()).remove(any());
        verify(byEqualityValue).remove(schedule.equalityCheckValue(), entityKey.getKeyAsLong());
    }

    @Test
    void expiresHandlesNotExistingInIds() {
        var entityKey = new EntityNumVirtualKey(fromScheduleId(created));
        given(byId.remove(entityKey)).willReturn(null);

        subject.expire(created);

        verify(byId).remove(new EntityNumVirtualKey(fromScheduleId(created)));

        verify(byExpirationSecond, never()).get(any());
        verify(byExpirationSecond, never()).remove(any());

        verify(byEquality, never()).get(any());
        verify(byEquality, never()).remove(any());
    }

    @Test
    void expiresHandlesNotExistingMaps() {
        var entityKey = new EntityNumVirtualKey(fromScheduleId(created));
        given(byId.remove(entityKey)).willReturn(schedule);

        subject.expire(created);

        verify(byId).remove(new EntityNumVirtualKey(fromScheduleId(created)));

        verify(byExpirationSecond).get(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(byExpirationSecond, never()).put(any(), any());
        verify(byExpirationSecond, never()).remove(any());

        verify(byEquality).get(new ScheduleEqualityVirtualKey(schedule.equalityCheckKey()));
        verify(byEquality, never()).put(any(), any());
        verify(byEquality, never()).remove(any());
    }

    @Test
    void throwsOnExpiringMissingSchedule() {
        given(byId.containsKey(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(false);

        assertThrows(IllegalArgumentException.class, () -> subject.expire(created));
    }

    @Test
    void throwsOnExpiringPending() {
        final var expected = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);

        subject.createProvisionally(expected, consensusTime);

        assertThrows(IllegalArgumentException.class, () -> subject.expire(subject.pendingId));
    }

    @Test
    void advanceCurrentMinSecondWorks() {
        given(schedules.getCurrentMinSecond()).willReturn(consensusTime.getSeconds());

        assertTrue(subject.advanceCurrentMinSecond(consensusTime.toJava().plusSeconds(5)));

        verify(byExpirationSecond, times(5)).containsKey(any());
        verify(byExpirationSecond)
                .containsKey(new SecondSinceEpocVirtualKey(consensusTime.getSeconds()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(1).getEpochSecond()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(2).getEpochSecond()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(3).getEpochSecond()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(4).getEpochSecond()));
        verify(schedules)
                .setCurrentMinSecond(consensusTime.toJava().plusSeconds(5).getEpochSecond());
    }

    @Test
    void advanceCurrentMinSecondStopsAtExisting() {
        given(schedules.getCurrentMinSecond()).willReturn(consensusTime.getSeconds());
        given(
                        byExpirationSecond.containsKey(
                                new SecondSinceEpocVirtualKey(
                                        consensusTime.toJava().plusSeconds(2).getEpochSecond())))
                .willReturn(true);

        assertTrue(subject.advanceCurrentMinSecond(consensusTime.toJava().plusSeconds(5)));

        verify(byExpirationSecond, times(3)).containsKey(any());
        verify(byExpirationSecond)
                .containsKey(new SecondSinceEpocVirtualKey(consensusTime.getSeconds()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(1).getEpochSecond()));
        verify(byExpirationSecond)
                .containsKey(
                        new SecondSinceEpocVirtualKey(
                                consensusTime.toJava().plusSeconds(2).getEpochSecond()));
        verify(schedules)
                .setCurrentMinSecond(consensusTime.toJava().plusSeconds(2).getEpochSecond());
    }

    @Test
    void advanceCurrentMinSecondReturnsFalseWhenNoChange() {
        given(schedules.getCurrentMinSecond()).willReturn(consensusTime.getSeconds());

        assertFalse(subject.advanceCurrentMinSecond(consensusTime.toJava()));

        verify(byExpirationSecond, never()).containsKey(any());
        verify(schedules, never()).setCurrentMinSecond(anyLong());
    }

    @Test
    void advanceCurrentMinSecondHandlesLargeValues() {
        given(schedules.getCurrentMinSecond()).willReturn(Long.MAX_VALUE);

        assertFalse(subject.advanceCurrentMinSecond(consensusTime.toJava()));

        given(schedules.getCurrentMinSecond()).willReturn(Instant.MAX.getEpochSecond());

        assertFalse(subject.advanceCurrentMinSecond(consensusTime.toJava()));

        verify(byExpirationSecond, never()).containsKey(any());
        verify(schedules, never()).setCurrentMinSecond(anyLong());
    }

    @Test
    void nextSchedulesToExpireWorks() {

        subject = spy(subject);
        doReturn(false).when(subject).advanceCurrentMinSecond(any());

        ScheduleID notExecutedId = IdUtils.asSchedule("0.0.331233");
        final var notExecuted = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        notExecuted.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));

        ScheduleID deletedId = IdUtils.asSchedule("0.0.311233");
        final var deleted = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        deleted.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));
        deleted.markDeleted(deleted.calculatedExpirationTime().toJava());

        ScheduleID extraId = IdUtils.asSchedule("0.0.311231");
        final var extra = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        extra.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));
        extra.markExecuted(extra.calculatedExpirationTime().toJava());

        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(notExecutedId))))
                .willReturn(notExecuted);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(deletedId)))).willReturn(deleted);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(extraId)))).willReturn(extra);

        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);
        given(schedule.isExecuted()).willReturn(true);

        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(expectedExpiry, 0),
                                        LongLists.immutable.of(fromScheduleId(created).longValue()),
                                        notExecuted.calculatedExpirationTime(),
                                        LongLists.immutable.of(
                                                fromScheduleId(deletedId).longValue(),
                                                fromScheduleId(notExecutedId).longValue(),
                                                fromScheduleId(extraId).longValue()))));

        var toExpire = subject.nextSchedulesToExpire(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(toExpire, ImmutableList.of(created, deletedId));

        verify(subject).advanceCurrentMinSecond(Instant.ofEpochSecond(expectedExpiry + 1));
    }

    @Test
    void nextSchedulesToExpireHandlesEmptyBySecond() {
        subject = spy(subject);
        doReturn(false).when(subject).advanceCurrentMinSecond(any());

        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);
        given(schedule.isExecuted()).willReturn(true);

        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(bySecondValue.asWritable()).willReturn(bySecondValue);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        given(bySecondValue.getIds()).willReturn(new TreeMap<>());

        var toExpire = subject.nextSchedulesToExpire(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(toExpire, ImmutableList.of());

        verify(bySecondValue).asWritable();
        verify(byExpirationSecond).remove(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(subject).advanceCurrentMinSecond(Instant.ofEpochSecond(expectedExpiry + 1));
    }

    @Test
    void nextSchedulesToExpireHandlesPastConsensusTime() {
        subject = spy(subject);
        doReturn(false).when(subject).advanceCurrentMinSecond(any());
        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);

        var toExpire = subject.nextSchedulesToExpire(Instant.ofEpochSecond(expectedExpiry));

        assertEquals(Collections.emptyList(), toExpire);

        verify(byExpirationSecond, never()).get(any());
        verify(subject).advanceCurrentMinSecond(Instant.ofEpochSecond(expectedExpiry));
    }

    @Test
    void nextSchedulesToExpireHandlesNoSchedulesAtCurrentSecond() {
        subject = spy(subject);
        doReturn(false).when(subject).advanceCurrentMinSecond(any());
        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);
        given(schedule.isExecuted()).willReturn(true);

        var toExpire = subject.nextSchedulesToExpire(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(Collections.emptyList(), toExpire);

        verify(byExpirationSecond).get(new SecondSinceEpocVirtualKey(expectedExpiry));
        verify(subject).advanceCurrentMinSecond(Instant.ofEpochSecond(expectedExpiry + 1));
    }

    @Test
    void nextSchedulesToExpireRemovesInvalidSchedules() {
        subject = spy(subject);
        doReturn(false).when(subject).advanceCurrentMinSecond(any());

        ScheduleID notExistingId = IdUtils.asSchedule("0.0.331233");

        ScheduleID badExpirationId = IdUtils.asSchedule("0.0.311233");
        final var badExpiration = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        badExpiration.setCalculatedExpirationTime(new RichInstant(expectedExpiry - 1, 0));
        badExpiration.markExecuted(badExpiration.calculatedExpirationTime().toJava());

        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(badExpirationId))))
                .willReturn(badExpiration);

        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);
        given(schedule.isExecuted()).willReturn(true);

        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(bySecondValue.asWritable()).willReturn(bySecondValue);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(expectedExpiry, 0),
                                                LongLists.immutable.of(
                                                        fromScheduleId(created).longValue(),
                                                        fromScheduleId(badExpirationId)
                                                                .longValue()),
                                        new RichInstant(expectedExpiry, 1),
                                                LongLists.immutable.of(
                                                        fromScheduleId(notExistingId)
                                                                .longValue()))));

        var toExpire = subject.nextSchedulesToExpire(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(toExpire, ImmutableList.of(created, badExpirationId));

        verify(bySecondValue).asWritable();
        verify(bySecondValue)
                .removeId(
                        new RichInstant(expectedExpiry, 1),
                        fromScheduleId(notExistingId).longValue());
        verify(bySecondValue)
                .removeId(
                        new RichInstant(expectedExpiry, 0),
                        fromScheduleId(badExpirationId).longValue());
        verify(byExpirationSecond, never()).remove(any());
        verify(subject).advanceCurrentMinSecond(Instant.ofEpochSecond(expectedExpiry + 1));
    }

    @Test
    void nextScheduleToEvaluateWorks() {

        ScheduleID notExecutedId = IdUtils.asSchedule("0.0.331233");
        final var notExecuted = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        notExecuted.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));

        ScheduleID deletedId = IdUtils.asSchedule("0.0.311233");
        final var deleted = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        deleted.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));
        deleted.markDeleted(deleted.calculatedExpirationTime().toJava());

        ScheduleID extraId = IdUtils.asSchedule("0.0.311231");
        final var extra = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        extra.setCalculatedExpirationTime(new RichInstant(expectedExpiry, 1));
        extra.markExecuted(extra.calculatedExpirationTime().toJava());

        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(notExecutedId))))
                .willReturn(notExecuted);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(deletedId)))).willReturn(deleted);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(extraId)))).willReturn(extra);

        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);

        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(expectedExpiry, 0),
                                        LongLists.immutable.of(fromScheduleId(created).longValue()),
                                        notExecuted.calculatedExpirationTime(),
                                        LongLists.immutable.of(
                                                fromScheduleId(notExecutedId).longValue(),
                                                fromScheduleId(deletedId).longValue(),
                                                fromScheduleId(extraId).longValue()),
                                        new RichInstant(expectedExpiry, 5),
                                        LongLists.immutable.of())));

        var toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(created, toEvaluate);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        notExecuted.calculatedExpirationTime(),
                                        LongLists.immutable.of(
                                                fromScheduleId(notExecutedId).longValue(),
                                                fromScheduleId(deletedId).longValue(),
                                                fromScheduleId(extraId).longValue()))));

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertEquals(toEvaluate, notExecutedId);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        notExecuted.calculatedExpirationTime(),
                                        LongLists.immutable.of(
                                                fromScheduleId(deletedId).longValue(),
                                                fromScheduleId(extraId).longValue()))));

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        notExecuted.calculatedExpirationTime(),
                                        LongLists.immutable.of())));

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        given(bySecondValue.getIds()).willReturn(new TreeMap<>());

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);
    }

    @Test
    void nextScheduleToEvaluateHandlesPastConsensusTime() {
        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);

        var toExpire = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry));

        assertNull(toExpire);

        verify(byExpirationSecond, never()).get(any());
    }

    @Test
    void nextScheduleToEvaluateHandlesNoSchedulesAtCurrentSecond() {
        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);

        var toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        verify(byExpirationSecond).get(new SecondSinceEpocVirtualKey(expectedExpiry));
    }

    @Test
    void nextScheduleToEvaluateHandlesInvalidSchedules() {
        ScheduleID notExistingId = IdUtils.asSchedule("0.0.331233");

        ScheduleID badExpirationId = IdUtils.asSchedule("0.0.311233");
        final var badExpiration = ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L);
        badExpiration.setCalculatedExpirationTime(new RichInstant(expectedExpiry - 1, 0));
        badExpiration.markExecuted(badExpiration.calculatedExpirationTime().toJava());

        given(byId.get(new EntityNumVirtualKey(fromScheduleId(created)))).willReturn(schedule);
        given(byId.get(new EntityNumVirtualKey(fromScheduleId(badExpirationId))))
                .willReturn(badExpiration);

        given(schedules.getCurrentMinSecond()).willReturn(expectedExpiry);

        var bySecondValue = mock(ScheduleSecondVirtualValue.class);
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(expectedExpiry, 0),
                                                LongLists.immutable.of(
                                                        fromScheduleId(notExistingId).longValue()),
                                        new RichInstant(expectedExpiry, 1),
                                                LongLists.immutable.of(
                                                        fromScheduleId(created).longValue()))));

        var toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        given(bySecondValue.getIds())
                .willReturn(
                        new TreeMap<>(
                                ImmutableMap.of(
                                        new RichInstant(expectedExpiry, 0),
                                                LongLists.immutable.of(
                                                        fromScheduleId(badExpirationId)
                                                                .longValue()),
                                        new RichInstant(expectedExpiry, 1),
                                                LongLists.immutable.of(
                                                        fromScheduleId(created).longValue()))));

        toEvaluate = subject.nextScheduleToEvaluate(Instant.ofEpochSecond(expectedExpiry + 1));

        assertNull(toEvaluate);

        verify(bySecondValue, never()).removeId(any(), anyLong());
        verify(byExpirationSecond, never()).remove(any());
        verify(byExpirationSecond, never()).getForModify(any());
    }

    @Test
    void getBySecondWorks() {
        var bySecondValue = new ScheduleSecondVirtualValue();
        given(byExpirationSecond.get(new SecondSinceEpocVirtualKey(expectedExpiry)))
                .willReturn(bySecondValue);

        assertEquals(subject.getBySecond(expectedExpiry), bySecondValue);
    }

    @Test
    void throwsUsoOnDelete() {
        assertThrows(UnsupportedOperationException.class, () -> subject.delete(created));
    }

    private void rejectWith(final ResponseCodeEnum expectedCode, final TransactionBody parentTxn) {
        final var outcome =
                subject.createProvisionally(
                        ScheduleVirtualValue.from(parentTxn.toByteArray(), 0L), consensusTime);

        assertEquals(expectedCode, outcome.status());
        assertNull(outcome.created());
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
    }
}
