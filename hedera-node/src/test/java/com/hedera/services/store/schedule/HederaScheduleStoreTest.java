package com.hedera.services.store.schedule;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.tokens.HederaTokenStore;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_SIGNER_ONE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_SIGNER_TWO_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class HederaScheduleStoreTest {
    static final int SIGNATURE_BYTES = 64;
    EntityIdSource ids;
    FCMap<MerkleEntityId, MerkleSchedule> schedules;
    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    HederaLedger hederaLedger;
    GlobalDynamicProperties globalDynamicProperties;

    MerkleSchedule schedule;
    MerkleSchedule anotherSchedule;
    MerkleAccount account;

    byte[] transactionBody;
    String entityMemo;
    int transactionBodyHashCode;
    RichInstant schedulingTXValidStart;
    RichInstant consensusTime;
    Key adminKey;
    JKey adminJKey;

    ScheduleID created = IdUtils.asSchedule("1.2.333333");
    AccountID schedulingAccount = IdUtils.asAccount("1.2.333");
    AccountID payerId = IdUtils.asAccount("1.2.456");
    AccountID anotherPayerId = IdUtils.asAccount("1.2.457");

    EntityId entityPayer = EntityId.ofNullableAccountId(payerId);
    EntityId entitySchedulingAccount = EntityId.ofNullableAccountId(schedulingAccount);

    long expectedExpiry = 1_234_567L;

    HederaScheduleStore subject;

    @BeforeEach
    public void setup() {
        transactionBody = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        entityMemo = "Some memo here";
        transactionBodyHashCode = Arrays.hashCode(transactionBody);
        schedulingTXValidStart = new RichInstant(123, 456);
        consensusTime = new RichInstant(expectedExpiry, 0);
        adminKey = SCHEDULE_ADMIN_KT.asKey();
        adminJKey = SCHEDULE_ADMIN_KT.asJKeyUnchecked();

        schedule = mock(MerkleSchedule.class);
        anotherSchedule = mock(MerkleSchedule.class);

        given(schedule.transactionBody()).willReturn(transactionBody);
        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.adminKey()).willReturn(Optional.of(SCHEDULE_ADMIN_KT.asJKeyUnchecked()));
        given(schedule.payer()).willReturn(EntityId.ofNullableAccountId(payerId));
        given(schedule.memo()).willReturn(Optional.of(entityMemo));

        given(anotherSchedule.payer()).willReturn(EntityId.ofNullableAccountId(anotherPayerId));

        ids = mock(EntityIdSource.class);
        given(ids.newScheduleId(schedulingAccount)).willReturn(created);

        account = mock(MerkleAccount.class);

        hederaLedger = mock(HederaLedger.class);

        globalDynamicProperties = mock(GlobalDynamicProperties.class);

        accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
        given(accountsLedger.exists(payerId)).willReturn(true);
        given(accountsLedger.exists(schedulingAccount)).willReturn(true);
        given(accountsLedger.get(payerId, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(false);

        schedules = (FCMap<MerkleEntityId, MerkleSchedule>) mock(FCMap.class);
        given(schedules.get(fromScheduleId(created))).willReturn(schedule);
        given(schedules.containsKey(fromScheduleId(created))).willReturn(true);

        subject = new HederaScheduleStore(globalDynamicProperties, ids, () -> schedules);
        subject.setAccountsLedger(accountsLedger);
        subject.setHederaLedger(hederaLedger);
    }

    @Test
    public void commitAndRollbackThrowIseIfNoPendingCreation() {
        // expect:
        assertThrows(IllegalStateException.class, subject::commitCreation);
        assertThrows(IllegalStateException.class, subject::rollbackCreation);
    }

    @Test
    public void commitPutsToMapAndClears() {
        // setup:
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        // when:
        subject.commitCreation();

        // then:
        verify(schedules).put(fromScheduleId(created), schedule);
        // and:
        assertSame(subject.pendingId, HederaScheduleStore.NO_PENDING_ID);
        assertNull(subject.pendingCreation);
    }

    @Test
    public void rollbackReclaimsIdAndClears() {
        // setup:
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        // when:
        subject.rollbackCreation();

        // then:
        verify(schedules, never()).put(fromScheduleId(created), schedule);
        verify(ids).reclaimLastId();
        // and:
        assertSame(subject.pendingId, HederaScheduleStore.NO_PENDING_ID);
        assertNull(subject.pendingCreation);
    }

    @Test
    public void understandsPendingCreation() {
        // expect:
        assertFalse(subject.isCreationPending());

        // and when:
        subject.pendingId = created;

        // expect:
        assertTrue(subject.isCreationPending());
    }

    @Test
    public void getThrowsIseOnMissing() {
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.get(created));
    }

    @Test
    public void applicationRejectsMissing() {
        // setup:
        var change = mock(Consumer.class);

        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // expect:
        assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void provisionalApplicationWorks() {
        // setup:
        Consumer<MerkleSchedule> change = mock(Consumer.class);
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        // when:
        subject.apply(created, change);

        // then:
		verify(change).accept(schedule);
        verify(schedules, never()).getForModify(fromScheduleId(created));
    }

    @Test
    public void applicationWorks() {
        // setup:
        var change = mock(Consumer.class);
        given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);
        // and:
        InOrder inOrder = Mockito.inOrder(change, schedules);

        // when:
        subject.apply(created, change);

        // then:
        inOrder.verify(schedules).getForModify(fromScheduleId(created));
        inOrder.verify(change).accept(schedule);
        inOrder.verify(schedules).replace(fromScheduleId(created), schedule);
    }

    @Test
    public void applicationAlwaysReplacesModifiableSchedule() {
        // setup:
        var change = mock(Consumer.class);
        var key = fromScheduleId(created);

        given(schedules.getForModify(key)).willReturn(schedule);

        willThrow(IllegalStateException.class).given(change).accept(any());

        // when:
        assertThrows(IllegalArgumentException.class, () -> subject.apply(created, change));

        // then:
        verify(schedules).replace(key, schedule);
    }

    @Test
    public void createProvisionallyWorks() {
        var expected = new MerkleSchedule(transactionBody, entitySchedulingAccount, schedulingTXValidStart);
        expected.setAdminKey(adminJKey);
        expected.setPayer(entityPayer);
        expected.setMemo(entityMemo);
        expected.setExpiry(expectedExpiry);

        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(OK, outcome.getStatus());
        assertEquals(created, outcome.getCreated().get());
        // and:
        assertEquals(created, subject.pendingId);
        assertEquals(expected, subject.pendingCreation);
    }

    @Test
    public void createProvisionallyMissingPayerFails() {
        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        null,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertTrue(outcome.getCreated().isEmpty());
    }

    @Test
    public void getCanReturnPending() {
        // setup:
        subject.pendingId = created;
        subject.pendingCreation = schedule;

        // expect:
        assertSame(schedule, subject.get(created));
    }

    @Test
    public void existenceCheckUnderstandsPendingIdOnlyAppliesIfCreationPending() {
        // expect:
        assertFalse(subject.exists(HederaScheduleStore.NO_PENDING_ID));
    }

    @Test
    public void rejectsCreateProvisionallyMissingPayer() {
        // given:
        given(accountsLedger.exists(payerId)).willReturn(false);

        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
    }

    @Test
    public void rejectsCreateProvisionallyDeletedPayer() {
        // given:
        given(accountsLedger.get(payerId, IS_DELETED)).willReturn(true);

        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
    }

    @Test
    public void rejectsCreateProvisionallyDeletedSchedulingAccount() {
        // given:
        given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(true);

        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
    }

    @Test
    public void rejectsCreateProvisionallyMissingSchedulingAccount() {
        // given:
        given(accountsLedger.exists(schedulingAccount)).willReturn(false);

        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        consensusTime,
                        Optional.of(adminJKey),
                        Optional.of(entityMemo));

        // then:
        assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
    }

    @Test
    public void getsScheduleID() {
        // given:
        CompositeKey txKey = new CompositeKey(
                transactionBodyHashCode,
                EntityId.ofNullableAccountId(payerId),
                adminKey,
                entityMemo);
        subject.txToEntityId.put(txKey, fromScheduleId(created));

        // when:
        var scheduleId = subject.lookupScheduleId(transactionBody, payerId, adminKey, entityMemo);

        assertEquals(Optional.of(created), scheduleId);
    }

    @Test
    public void getsScheduleIDFromPending() {
        // given:
        subject.pendingCreation = schedule;
        subject.pendingId = created;

        // when:
        var scheduleId = subject.lookupScheduleId(transactionBody, payerId, adminKey, entityMemo);

        assertEquals(Optional.of(created), scheduleId);
    }

    @Test
    public void failsToGetScheduleID() {
        // when:
        var scheduleId = subject.lookupScheduleId(transactionBody, payerId, adminKey, entityMemo);

        assertTrue(scheduleId.isEmpty());
    }

    @Test
    public void deletesAsExpected() {
        // given:
        given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

        // when:
        var outcome = subject.delete(created);

        // then:
        verify(schedules, times(3)).containsKey(fromScheduleId(created));
        verify(schedules).remove(fromScheduleId(created));
        // and:
        assertEquals(OK, outcome);
    }

    @Test
    public void rejectsDeletionMissingAdminKey() {
        // given:
        given(schedule.adminKey()).willReturn(Optional.empty());

        // when:
        var outcome = subject.delete(created);

        // then:
        verify(schedules, never()).remove(fromScheduleId(created));
        // and:
        assertEquals(SCHEDULE_IS_IMMUTABLE, outcome);
    }

    @Test
    public void rejectsDeletionMissingSchedule() {
        // given:
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // when:
        var outcome = subject.delete(created);

        // then:
        verify(schedules, never()).remove(fromScheduleId(created));
        // and:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    public void rejectsExecutionMissingSchedule() {
        // given:
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // when:
        var outcome = subject.markAsExecuted(created);

        // then:
        verify(schedules, never()).remove(fromScheduleId(created));
        // and:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    public void executesAsExpected() {
        // given:
        given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

        // when:
        var outcome = subject.markAsExecuted(created);

        // then:
        verify(schedules, times(3)).containsKey(fromScheduleId(created));
        verify(schedules).remove(fromScheduleId(created));
        // and:
        assertEquals(OK, outcome);
    }

    @Test
    public void expiresAsExpected() {
        // when:
        subject.expire(EntityId.ofNullableScheduleId(created));

        // then:
        verify(schedules, times(3)).containsKey(fromScheduleId(created));
        verify(schedules).remove(fromScheduleId(created));
    }
}
