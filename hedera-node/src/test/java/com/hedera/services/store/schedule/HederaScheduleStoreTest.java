package com.hedera.services.store.schedule;

/*-
 * ‌
 * Hedera Services Node
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_SIGNER_ONE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_SIGNER_TWO_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;
import static com.swirlds.common.CommonUtils.hex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


public class HederaScheduleStoreTest {
    static final int SIGNATURE_BYTES = 64;
    EntityIdSource ids;
    FCMap<MerkleEntityId, MerkleSchedule> schedules;
    TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    HederaLedger hederaLedger;

    MerkleSchedule schedule;
    MerkleSchedule anotherSchedule;
    MerkleAccount account;

    byte[] transactionBody;
    int transactionBodyHashCode;
    RichInstant schedulingTXValidStart;
    Key adminKey;
    JKey adminJKey;
    JKey signer1, signer2;
    Set<JKey> signers;

    ScheduleID created = IdUtils.asSchedule("1.2.333333");
    AccountID schedulingAccount = IdUtils.asAccount("1.2.333");
    AccountID payerId = IdUtils.asAccount("1.2.456");
    AccountID anotherPayerId = IdUtils.asAccount("1.2.457");

    EntityId entityPayer = EntityId.ofNullableAccountId(payerId);
    EntityId entitySchedulingAccount = EntityId.ofNullableAccountId(schedulingAccount);

    HederaScheduleStore subject;

    @BeforeEach
    public void setup() {
        transactionBody = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        transactionBodyHashCode = Arrays.hashCode(transactionBody);
        schedulingTXValidStart = new RichInstant(123, 456);
        adminKey = SCHEDULE_ADMIN_KT.asKey();
        adminJKey = SCHEDULE_ADMIN_KT.asJKeyUnchecked();

        signer1 = SCHEDULE_SIGNER_ONE_KT.asJKeyUnchecked();
        signer2 = SCHEDULE_SIGNER_TWO_KT.asJKeyUnchecked();
        signers = new LinkedHashSet<>();
        signers.add(signer1);
        signers.add(signer2);

        schedule = mock(MerkleSchedule.class);
        anotherSchedule = mock(MerkleSchedule.class);

        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.adminKey()).willReturn(Optional.of(SCHEDULE_ADMIN_KT.asJKeyUnchecked()));
        given(schedule.signers()).willReturn(signers);
        given(schedule.payer()).willReturn(EntityId.ofNullableAccountId(payerId));

        given(anotherSchedule.payer()).willReturn(EntityId.ofNullableAccountId(anotherPayerId));

        ids = mock(EntityIdSource.class);
        given(ids.newScheduleId(schedulingAccount)).willReturn(created);

        account = mock(MerkleAccount.class);

        hederaLedger = mock(HederaLedger.class);

        accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
        given(accountsLedger.exists(payerId)).willReturn(true);
        given(accountsLedger.exists(schedulingAccount)).willReturn(true);
        given(accountsLedger.get(payerId, IS_DELETED)).willReturn(false);
        given(accountsLedger.get(schedulingAccount, IS_DELETED)).willReturn(false);

        schedules = (FCMap<MerkleEntityId, MerkleSchedule>) mock(FCMap.class);
        given(schedules.get(fromScheduleId(created))).willReturn(schedule);
        given(schedules.containsKey(fromScheduleId(created))).willReturn(true);

        subject = new HederaScheduleStore(ids, () -> schedules);
        subject.setAccountsLedger(accountsLedger);
        subject.setHederaLedger(hederaLedger);
    }

    @Test
    public void rejectsDeletionScheduleAlreadyDeleted() {
        // given:
        given(schedule.isDeleted()).willReturn(true);

        // when:
        var outcome = subject.delete(created);

        // expect:
        assertEquals(SCHEDULE_WAS_DELETED, outcome);
    }

    @Test
    public void successfulAddSigner() {
        // when:
        var signers = new HashSet<JKey>();
        signers.add(signer1);
        var outcome = subject.addSigners(created, signers);

        // expect:
        assertEquals(OK, outcome);
    }


    @Test
    public void failAddSignerNotExistingSchedule() {
        // given:
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // when:
        var outcome = subject.addSigners(created, signers);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    public void failAddSignerDeletedSchedule() {
        // given:
        given(schedule.isDeleted()).willReturn(true);

        // when:
        var outcome = subject.addSigners(created, signers);

        // expect:
        assertEquals(SCHEDULE_WAS_DELETED, outcome);
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
        subject.pendingTxHashCode = 123;

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
        // when:
        var outcome = subject
                .createProvisionally(
                        transactionBody,
                        payerId,
                        schedulingAccount,
                        schedulingTXValidStart,
                        Optional.of(adminJKey));

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
                        Optional.of(adminJKey));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertTrue(outcome.getCreated().isEmpty());
    }

    @Test
    public void getCanReturnPending() {
        // setup:
        subject.pendingId = created;
        subject.pendingCreation = schedule;
        subject.pendingTxHashCode = transactionBodyHashCode;

        // expect:
        assertSame(schedule, subject.get(created));
        assertEquals(transactionBodyHashCode, subject.pendingTxHashCode);
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
                        Optional.of(adminJKey));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
        assertNull(subject.pendingTxHashCode);
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
                        Optional.of(adminJKey));

        // then:
        assertEquals(INVALID_SCHEDULE_PAYER_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
        assertNull(subject.pendingTxHashCode);
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
                        Optional.of(adminJKey));

        // then:
        assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
        assertNull(subject.pendingTxHashCode);
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
                        Optional.of(adminJKey));

        // then:
        assertEquals(INVALID_SCHEDULE_ACCOUNT_ID, outcome.getStatus());
        assertEquals(Optional.empty(), outcome.getCreated());
        // and:
        assertNull(subject.pendingCreation);
        assertEquals(ScheduleID.getDefaultInstance(), subject.pendingId);
        assertNull(subject.pendingTxHashCode);
    }

    @Test
    public void getsScheduleID() {
        // given:
        subject.txToEntityId.put(new CompositeKey(transactionBodyHashCode, payerId), fromScheduleId(created));
        given(subject.get(created)).willReturn(schedule);

        // when:
        var scheduleId = subject.getScheduleID(transactionBody, payerId);

        assertEquals(Optional.of(created), scheduleId);
    }

    @Test
    public void getsScheduleIDFromPending() {
        // given:
        subject.pendingCreation = schedule;
        subject.pendingId = created;
        subject.pendingTxHashCode = transactionBodyHashCode;

        // when:
        var scheduleId = subject.getScheduleID(transactionBody, payerId);

        assertEquals(Optional.of(created), scheduleId);
    }

    @Test
    public void failsToGetScheduleID() {
        // when:
        var scheduleId = subject.getScheduleID(transactionBody, payerId);

        assertTrue(scheduleId.isEmpty());
    }

    @Test
    public void deletesAsExpected() {
        // given:
        given(schedules.getForModify(fromScheduleId(created))).willReturn(schedule);

        // when:
        var outcome = subject.delete(created);

        // then:
        assertEquals(OK, outcome);
    }

    @Test
    public void rejectsDeletionMissingAdminKey() {
        // given:
        given(schedule.adminKey()).willReturn(Optional.empty());

        // when:
        var outcome = subject.delete(created);

        // then:
        assertEquals(SCHEDULE_IS_IMMUTABLE, outcome);
    }

    @Test
    public void rejectsDeletionMissingSchedule() {
        // given:
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // when:
        var outcome = subject.delete(created);

        // then:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    public void validCompositeKey() {
        // given:
        var key = new CompositeKey(transactionBodyHashCode, payerId);

        assertEquals(key, key);
    }

    @Test
    public void validDifferentInstanceKey() {
        // given:
        var key = new CompositeKey(transactionBodyHashCode, payerId);

        assertNotEquals(key, new Object());
    }
}
