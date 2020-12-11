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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static com.hedera.services.state.merkle.MerkleEntityId.fromScheduleId;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_WAS_DELETED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    MerkleAccount account;

    byte[] transactionBody;
    Key adminKey;
    JKey adminJKey;
    EntityId signer1, signer2;
    byte[] signature1, signature2;
    HashSet<EntityId> signers;
    Map<EntityId, byte[]> signatures;

    ScheduleID created = IdUtils.asSchedule("1.2.333333");
    AccountID sponsor = IdUtils.asAccount("1.2.333");

    HederaScheduleStore subject;

    @BeforeEach
    public void setup() {
        transactionBody = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        adminKey = SCHEDULE_ADMIN_KT.asKey();
        adminJKey = SCHEDULE_ADMIN_KT.asJKeyUnchecked();

        signer1 = new EntityId(1, 2, 3);
        signer2 = new EntityId(2, 3, 4);
        signers = new HashSet<>();
        signers.add(signer1);
        signers.add(signer2);

        signature1 = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);
        signature2 = TxnUtils.randomUtf8Bytes(SIGNATURE_BYTES);

        signatures = new HashMap<>();
        signatures.put(signer1, signature1);
        signatures.put(signer2, signature2);

        schedule = mock(MerkleSchedule.class);

        given(schedule.hasAdminKey()).willReturn(true);
        given(schedule.adminKey()).willReturn(Optional.of(SCHEDULE_ADMIN_KT.asJKeyUnchecked()));
        given(schedule.signatures()).willReturn(signatures);

        ids = mock(EntityIdSource.class);
        given(ids.newScheduleId(sponsor)).willReturn(created);

        account = mock(MerkleAccount.class);

        hederaLedger = mock(HederaLedger.class);

        accountsLedger = (TransactionalLedger<AccountID, AccountProperty, MerkleAccount>) mock(TransactionalLedger.class);
        given(accountsLedger.exists(sponsor)).willReturn(true);

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
    public void successfulPutSignature() {
        // when:
        var outcome = subject.putSignature(created, sponsor, signature1);

        // expect:
        assertEquals(OK, outcome);
    }

    @Test
    public void failPutSignatureDeletedAccount() {
        // given:
        given(hederaLedger.isDeleted(sponsor)).willReturn(true);

        // when:
        var outcome = subject.putSignature(created, sponsor, signature1);

        // expect:
        assertEquals(ACCOUNT_DELETED, outcome);
    }

    @Test
    public void failPutSignatureNotExistingSchedule() {
        // given:
        given(schedules.containsKey(fromScheduleId(created))).willReturn(false);

        // when:
        var outcome = subject.putSignature(created, sponsor, signature1);

        // expect:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
    }

    @Test
    public void failPutSignatureDeletedSchedule() {
        // given:
        given(schedule.isDeleted()).willReturn(true);

        // when:
        var outcome = subject.putSignature(created, sponsor, signature1);

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
    public void applicationAlwaysReplacesModifiableToken() {
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
        var expected = new MerkleSchedule(transactionBody, signers, signatures);
        expected.setAdminKey(adminJKey);
        // when:
        var outcome = subject
                .createProvisionally(transactionBody,
                    signers,
                    signatures,
                    Optional.of(adminJKey),
                    sponsor);

        // then:
        assertEquals(OK, outcome.getStatus());
        assertEquals(created, outcome.getCreated().get());
        // and:
        assertEquals(created, subject.pendingId);
        assertEquals(expected, subject.pendingCreation);
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
    public void rejectsMissingDeletion() {
        // given:
        var mockSubject = mock(ScheduleStore.class);

        given(mockSubject.resolve(created)).willReturn(ScheduleStore.MISSING_SCHEDULE);
        willCallRealMethod().given(mockSubject).delete(created);

        // when:
        var outcome = mockSubject.delete(created);

        // then:
        assertEquals(INVALID_SCHEDULE_ID, outcome);
        verify(mockSubject, never()).apply(any(), any());
    }
}
