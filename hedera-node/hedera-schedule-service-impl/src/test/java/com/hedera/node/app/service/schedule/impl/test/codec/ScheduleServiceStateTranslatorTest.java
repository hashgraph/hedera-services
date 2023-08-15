/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.test.codec;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.schedule.impl.codec.ScheduleServiceStateTranslator;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.test.utils.IdUtils;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceStateTranslatorTest {
    // spotless mangles this section randomly, due to incorrect wrapping rules
    // spotless:off
    // A few random values for fake ed25519 test keys
    private static final String PAYER_KEY_HEX =
            "badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada";
    private static final String SCHEDULER_KEY_HEX =
            "feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad";
    // This one is a perfect 10.
    private static final String ADMIN_KEY_HEX =
            "0000000000191561942608236107294793378084303638130997321548169216";
    private static final ProtoString EXEPECTED_HASH_STRING =
            new ProtoString("06abd7245b5e40473719948f7dee8120de725e41aa5a72cde8001f72c940ca7d");
    private static final String SCHEDULED_TXN_MEMO = "Wait for me!";
    private static final long FEE = 123L;
    private final String memo = "Test";
    private final long testExpiry = 123L;
    private final ScheduleID testScheduleID = ScheduleID.newBuilder().scheduleNum(1001L).build();
    private final Key adminKey = Key.newBuilder().ed25519(Bytes.fromHex(ADMIN_KEY_HEX)).build();
    private final AccountID scheduler = AccountID.newBuilder().accountNum(100L).build();
    private final Key schedulerKey = Key.newBuilder().ed25519(Bytes.fromHex(SCHEDULER_KEY_HEX)).build();
    private final AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    private final Key payerKey = Key.newBuilder().ed25519(Bytes.fromHex(PAYER_KEY_HEX)).build();
    private final Timestamp testValidStart = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    private final Timestamp expirationTime = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    private final Timestamp calculatedExpirationTime = Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    private final List<Key> keyList = Arrays.asList(adminKey, schedulerKey, payerKey);
    private final byte[] fpk = Objects.requireNonNull(payerKey.ed25519()).toByteArray();
    private final byte[] spk = Objects.requireNonNull(schedulerKey.ed25519()).toByteArray();
    private final byte[] tpk = Objects.requireNonNull(adminKey.ed25519()).toByteArray();
    private final long expiry = 2281580449L;
    private final RichInstant providedExpiry = new RichInstant(2281580449L, 0);
    private final boolean waitForExpiry = true;
    private final String entityMemo = "Test";
    private final EntityId schedulingAccount = new EntityId(0, 0, 100L);
    private final RichInstant schedulingTXValidStart = new RichInstant(2281580449L, 0);
    private final JKey adminKeyVirtual = (JKey) PbjConverter.fromPbjKey(adminKey).orElse(null);
    private final EntityId payerVirtual = new EntityId(0, 0, 2001L);
    // spotless:on

    // Non-Mock objects that require constructor initialization to avoid unnecessary statics
    private final com.hederahashgraph.api.proto.java.SchedulableTransactionBody scheduledTxn;
    private final com.hederahashgraph.api.proto.java.TransactionID parentTxnId;
    private final com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody creation;
    private final com.hederahashgraph.api.proto.java.TransactionBody parentTxn;
    private final byte[] bodyBytes;

    // Non-Mock objects, but may contain or reference mock objects.
    private ReadableScheduleStoreImpl scheduleStore;
    private Configuration testConfig;
    private SchedulableTransactionBody scheduled;
    private TransactionBody originalCreateTransaction;
    private List<byte[]> signatories;
    private ScheduleVirtualValue subject;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<ScheduleID, Schedule> schedulesById;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<ProtoLong, ScheduleList> schedulesByLong;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<ProtoString, ScheduleList> schedulesByString;

    ScheduleServiceStateTranslatorTest() {
        scheduledTxn = createScheduledTransaction();
        parentTxnId = getParentTransactionId();
        creation = getCreateTransactionBody();
        parentTxn = getParentTransaction();
        bodyBytes = parentTxn.toByteArray();
    }

    @BeforeEach
    void setup() {
        signatories = new ArrayList<>();

        subject = ScheduleVirtualValue.from(bodyBytes, expiry);
        subject.setKey(new EntityNumVirtualKey(3L));
        subject.witnessValidSignature(fpk);
        subject.witnessValidSignature(spk);
        subject.witnessValidSignature(tpk);
    }

    @Test
    void createScheduleVirtualValueFromSchedule() throws IOException {
        final com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                ScheduleServiceStateTranslator.pbjToState(getValidSchedule());

        Assertions.assertNotNull(scheduleVirtualValue);
        Assertions.assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        org.assertj.core.api.Assertions.assertThat(scheduleVirtualValue.signatories())
                .containsAll(subject.signatories());
        Assertions.assertEquals(scheduleVirtualValue.memo(), subject.memo());
        Assertions.assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        Assertions.assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        Assertions.assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        Assertions.assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        Assertions.assertEquals(scheduleVirtualValue.payer(), subject.payer());
        Assertions.assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        Assertions.assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        Assertions.assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        Assertions.assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        Assertions.assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        Assertions.assertEquals(
                subject.ordinaryViewOfScheduledTxn(), scheduleVirtualValue.ordinaryViewOfScheduledTxn());
        Assertions.assertEquals(subject.scheduledTxn(), scheduleVirtualValue.scheduledTxn());
        Assertions.assertArrayEquals(subject.bodyBytes(), scheduleVirtualValue.bodyBytes());
    }

    @Test
    void createScheduleFromScheduleVirtualValue() throws IOException {
        final Schedule schedule = ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(subject);
        Schedule expected = getValidSchedule();

        Assertions.assertNotNull(schedule);
        Assertions.assertEquals(
                expected.signatories().size(), schedule.signatories().size());
        org.assertj.core.api.Assertions.assertThat(expected.signatories()).containsAll(schedule.signatories());
        Assertions.assertEquals(expected.memo(), schedule.memo());
        Assertions.assertEquals(expected.deleted(), schedule.deleted());
        Assertions.assertEquals(expected.executed(), schedule.executed());
        Assertions.assertEquals(expected.adminKey(), schedule.adminKey());
        Assertions.assertEquals(expected.waitForExpiry(), schedule.waitForExpiry());
        Assertions.assertEquals(expected.payerAccountId(), schedule.payerAccountId());
        Assertions.assertEquals(expected.schedulerAccountId(), schedule.schedulerAccountId());
        Assertions.assertEquals(expected.scheduleValidStart(), schedule.scheduleValidStart());
        Assertions.assertEquals(expected.providedExpirationSecond(), schedule.providedExpirationSecond());
        Assertions.assertEquals(expected.calculatedExpirationSecond(), schedule.calculatedExpirationSecond());
        Assertions.assertEquals(expected.resolutionTime(), schedule.resolutionTime());
        Assertions.assertEquals(expected.originalCreateTransaction(), schedule.originalCreateTransaction());
        Assertions.assertNotNull(expected.scheduledTransaction());
        Assertions.assertNotNull(schedule.scheduledTransaction());
        Assertions.assertArrayEquals(
                PbjConverter.asBytes(SchedulableTransactionBody.PROTOBUF, expected.scheduledTransaction()),
                PbjConverter.asBytes(SchedulableTransactionBody.PROTOBUF, schedule.scheduledTransaction()));
    }

    @Test
    void createScheduleVirtualValueFromScheduleUsingReadableScheduleStoreByScheduleId() throws InvalidKeyException {
        BDDMockito.given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(getValidSchedule());

        final com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                ScheduleServiceStateTranslator.pbjToState(testScheduleID, scheduleStore);
        Assertions.assertNotNull(scheduleVirtualValue);
        Assertions.assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        org.assertj.core.api.Assertions.assertThat(scheduleVirtualValue.signatories())
                .containsAll(subject.signatories());
        Assertions.assertEquals(scheduleVirtualValue.memo(), subject.memo());
        Assertions.assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        Assertions.assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        Assertions.assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        Assertions.assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        Assertions.assertEquals(scheduleVirtualValue.payer(), subject.payer());
        Assertions.assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        Assertions.assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        Assertions.assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        Assertions.assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        Assertions.assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        Assertions.assertEquals(
                scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        Assertions.assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        Assertions.assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
    }

    @Test
    void createScheduleVirtualValueFromScheduleUsingReadableScheduleStoreByLong() throws InvalidKeyException {
        BDDMockito.given(states.<ProtoLong, ScheduleList>get("SCHEDULES_BY_EXPIRY_SEC"))
                .willReturn(schedulesByLong);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesByLong.get(new ProtoLong(testExpiry)))
                .willReturn(new ScheduleList(List.of(getValidSchedule())));

        final List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue>
                listScheduleVirtualValue = ScheduleServiceStateTranslator.pbjToState(testExpiry, scheduleStore);
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                listScheduleVirtualValue.get(0);
        Assertions.assertNotNull(scheduleVirtualValue);
        Assertions.assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        org.assertj.core.api.Assertions.assertThat(scheduleVirtualValue.signatories())
                .containsAll(subject.signatories());
        Assertions.assertEquals(scheduleVirtualValue.memo(), subject.memo());
        Assertions.assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        Assertions.assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        Assertions.assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        Assertions.assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        Assertions.assertEquals(scheduleVirtualValue.payer(), subject.payer());
        Assertions.assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        Assertions.assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        Assertions.assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        Assertions.assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        Assertions.assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        Assertions.assertEquals(
                scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        Assertions.assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        Assertions.assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
    }

    @Test
    void createScheduleVirtualValueFromScheduleUsingReadableScheduleStoreBySchedule() throws InvalidKeyException {
        Schedule schedule = getValidSchedule();
        BDDMockito.given(states.<ProtoString, ScheduleList>get("SCHEDULES_BY_EQUALITY"))
                .willReturn(schedulesByString);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesByString.get(EXEPECTED_HASH_STRING)).willReturn(new ScheduleList(List.of(schedule)));

        final List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue>
                listScheduleVirtualValue = ScheduleServiceStateTranslator.pbjToState(schedule, scheduleStore);
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                listScheduleVirtualValue.get(0);
        Assertions.assertNotNull(scheduleVirtualValue);
        Assertions.assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        org.assertj.core.api.Assertions.assertThat(scheduleVirtualValue.signatories())
                .containsAll(subject.signatories());
        Assertions.assertEquals(scheduleVirtualValue.memo(), subject.memo());
        Assertions.assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        Assertions.assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        Assertions.assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        Assertions.assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        Assertions.assertEquals(scheduleVirtualValue.payer(), subject.payer());
        Assertions.assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        Assertions.assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        Assertions.assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        Assertions.assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        Assertions.assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        Assertions.assertEquals(
                scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        Assertions.assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        Assertions.assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
    }

    private Schedule getValidSchedule() {
        testConfig = HederaTestConfigBuilder.create().getOrCreateConfig();
        scheduled = createSampleScheduled();
        originalCreateTransaction = originalCreateTransaction(scheduled, payer, adminKey);

        return Schedule.newBuilder()
                .deleted(false)
                .executed(false)
                .waitForExpiry(true)
                .memo(memo)
                .scheduleId(testScheduleID)
                .schedulerAccountId(scheduler)
                .payerAccountId(payer)
                .adminKey(adminKey)
                .scheduleValidStart(testValidStart)
                .providedExpirationSecond(expirationTime.seconds())
                .calculatedExpirationSecond(calculatedExpirationTime.seconds())
                .scheduledTransaction(scheduled)
                .originalCreateTransaction(originalCreateTransaction)
                .signatories(keyList)
                .build();
    }

    private SchedulableTransactionBody createSampleScheduled() {
        final SchedulableTransactionBody scheduledTxn = SchedulableTransactionBody.newBuilder()
                .cryptoDelete(CryptoDeleteTransactionBody.newBuilder()
                        .deleteAccountID(AccountID.newBuilder().accountNum(2).build())
                        .transferAccountID(
                                AccountID.newBuilder().accountNum(75231).build()))
                .transactionFee(FEE)
                .memo(SCHEDULED_TXN_MEMO)
                .build();
        return scheduledTxn;
    }

    private TransactionBody originalCreateTransaction(
            @NonNull final SchedulableTransactionBody childTransaction,
            @Nullable final AccountID explicitPayer,
            @Nullable final Key adminKey) {
        final TransactionID createdTransactionId = TransactionID.newBuilder()
                .accountID(scheduler)
                .transactionValidStart(testValidStart)
                .nonce(4444)
                .scheduled(false)
                .build();
        final ScheduleCreateTransactionBody.Builder builder = ScheduleCreateTransactionBody.newBuilder()
                .scheduledTransactionBody(childTransaction)
                .payerAccountID(payer)
                .waitForExpiry(true)
                .expirationTime(expirationTime)
                .adminKey(adminKey)
                .memo(memo);
        if (explicitPayer != null) builder.payerAccountID(explicitPayer);
        if (adminKey != null) builder.adminKey(adminKey);
        return TransactionBody.newBuilder()
                .transactionID(createdTransactionId)
                .scheduleCreate(builder.build())
                .build();
    }

    @NonNull
    private com.hederahashgraph.api.proto.java.TransactionID getParentTransactionId() {
        return com.hederahashgraph.api.proto.java.TransactionID.newBuilder()
                .setTransactionValidStart(MiscUtils.asTimestamp(schedulingTXValidStart.toJava()))
                .setNonce(4444)
                .setAccountID(schedulingAccount.toGrpcAccountId())
                .build();
    }

    @NonNull
    private com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody getCreateTransactionBody() {
        return com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody.newBuilder()
                .setAdminKey(MiscUtils.asKeyUnchecked(adminKeyVirtual))
                .setPayerAccountID(payerVirtual.toGrpcAccountId())
                .setExpirationTime(providedExpiry.toGrpc())
                .setWaitForExpiry(waitForExpiry)
                .setMemo(entityMemo)
                .setScheduledTransactionBody(scheduledTxn)
                .build();
    }

    @NonNull
    private com.hederahashgraph.api.proto.java.TransactionBody getParentTransaction() {
        return com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
                .setTransactionID(parentTxnId)
                .setScheduleCreate(creation)
                .build();
    }

    @NonNull
    private com.hederahashgraph.api.proto.java.SchedulableTransactionBody createScheduledTransaction() {
        return com.hederahashgraph.api.proto.java.SchedulableTransactionBody.newBuilder()
                .setTransactionFee(FEE)
                .setMemo(SCHEDULED_TXN_MEMO)
                .setCryptoDelete(com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody.newBuilder()
                        .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
                        .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
                .build();
    }
}
