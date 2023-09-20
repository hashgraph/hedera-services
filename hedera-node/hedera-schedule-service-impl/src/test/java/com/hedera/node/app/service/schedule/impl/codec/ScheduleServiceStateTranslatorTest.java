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

package com.hedera.node.app.service.schedule.impl.codec;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.BDDAssertions.assertThat;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.schedule.impl.ReadableScheduleStoreImpl;
import com.hedera.node.app.service.schedule.impl.ScheduleStoreTestBase;
import com.hedera.node.app.spi.state.ReadableKVStateBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.Codec;
import com.hedera.test.utils.IdUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceStateTranslatorTest extends ScheduleStoreTestBase {
    private static final long FEE = 123L;
    private final List<Key> keyList = Arrays.asList(adminKey, schedulerKey, payerKey);
    private final byte[] payerKeyBytes = requireNonNull(payerKey.ed25519()).toByteArray();
    private final byte[] schedulerKeyBytes =
            requireNonNull(schedulerKey.ed25519()).toByteArray();
    private final byte[] adminKeyBytes = requireNonNull(adminKey.ed25519()).toByteArray();
    private final long expirationSecond = 2281580449L;
    private final RichInstant providedExpiry = new RichInstant(expirationSecond, 0);
    private final boolean waitForExpiry = true;
    private final String entityMemo = "Test";
    private final EntityId schedulingAccount = new EntityId(0, 0, 1001L);
    private final RichInstant schedulingTXValidStart = new RichInstant(2281580449L, 0);
    private final JKey adminKeyVirtual =
            (JKey) PbjConverter.fromPbjKey(adminKey).orElse(null);
    private final EntityId payerVirtual = new EntityId(0, 0, 2001L);

    // Non-Mock objects that require constructor initialization to avoid unnecessary statics
    private final com.hederahashgraph.api.proto.java.SchedulableTransactionBody scheduledTxn;
    private final com.hederahashgraph.api.proto.java.TransactionID parentTxnId;
    private final com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody creation;
    private final com.hederahashgraph.api.proto.java.TransactionBody parentTxn;
    private final byte[] bodyBytes;

    // Non-Mock objects, but may contain or reference mock objects.
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
    void setup() throws PreCheckException, InvalidKeyException {
        setUpBase();
        signatories = new LinkedList<>();
        subject = ScheduleVirtualValue.from(bodyBytes, expirationSecond);
        subject.setKey(new EntityNumVirtualKey(3L));
        subject.witnessValidSignature(payerKeyBytes);
        subject.witnessValidSignature(schedulerKeyBytes);
        subject.witnessValidSignature(adminKeyBytes);
    }

    @Test
    void verifyTypicalForwardTranslation() throws IOException {
        final ScheduleVirtualValue scheduleVirtualValue = ScheduleServiceStateTranslator.pbjToState(getValidSchedule());

        assertThat(scheduleVirtualValue).isNotNull();
        final List<byte[]> expectedSignatories = scheduleVirtualValue.signatories();
        final List<byte[]> actualSignatories = subject.signatories();
        assertThat(actualSignatories).isNotNull();
        assertThat(expectedSignatories).containsExactlyInAnyOrderElementsOf(actualSignatories);
        assertThat(scheduleVirtualValue.memo()).isEqualTo(subject.memo());
        assertThat(scheduleVirtualValue.isDeleted()).isEqualTo(subject.isDeleted());
        assertThat(scheduleVirtualValue.isExecuted()).isEqualTo(subject.isExecuted());
        assertThat(scheduleVirtualValue.adminKey()).isEqualTo(subject.adminKey());
        assertThat(scheduleVirtualValue.waitForExpiryProvided()).isEqualTo(subject.waitForExpiryProvided());
        assertThat(scheduleVirtualValue.payer()).isEqualTo(subject.payer());
        assertThat(scheduleVirtualValue.schedulingAccount()).isEqualTo(subject.schedulingAccount());
        assertThat(scheduleVirtualValue.schedulingTXValidStart()).isEqualTo(subject.schedulingTXValidStart());
        assertThat(scheduleVirtualValue.expirationTimeProvided()).isEqualTo(subject.expirationTimeProvided());
        assertThat(scheduleVirtualValue.calculatedExpirationTime()).isEqualTo(subject.calculatedExpirationTime());
        assertThat(scheduleVirtualValue.getResolutionTime()).isEqualTo(subject.getResolutionTime());
        assertThat(subject.ordinaryViewOfScheduledTxn()).isEqualTo(scheduleVirtualValue.ordinaryViewOfScheduledTxn());
        assertThat(subject.scheduledTxn()).isEqualTo(scheduleVirtualValue.scheduledTxn());
        assertThat(subject.bodyBytes()).containsExactly(scheduleVirtualValue.bodyBytes());
    }

    @Test
    void verifyTypicalReverseTranslation() throws IOException {
        final Codec<SchedulableTransactionBody> protobufCodec = SchedulableTransactionBody.PROTOBUF;
        final Schedule schedule = ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(subject);
        Schedule expected = getValidSchedule();

        assertThat(schedule).isNotNull();
        final List<Key> expectedSignatories = expected.signatories();
        final List<Key> actualSignatories = schedule.signatories();
        assertThat(actualSignatories).isNotNull();
        assertThat(expectedSignatories).containsExactlyInAnyOrderElementsOf(actualSignatories);
        assertThat(expected.memo()).isEqualTo(schedule.memo());
        assertThat(expected.deleted()).isEqualTo(schedule.deleted());
        assertThat(expected.executed()).isEqualTo(schedule.executed());
        assertThat(expected.adminKey()).isEqualTo(schedule.adminKey());
        assertThat(expected.waitForExpiry()).isEqualTo(schedule.waitForExpiry());
        assertThat(expected.payerAccountId()).isEqualTo(schedule.payerAccountId());
        assertThat(expected.schedulerAccountId()).isEqualTo(schedule.schedulerAccountId());
        assertThat(expected.scheduleValidStart()).isEqualTo(schedule.scheduleValidStart());
        assertThat(expected.providedExpirationSecond()).isEqualTo(schedule.providedExpirationSecond());
        assertThat(expected.calculatedExpirationSecond()).isEqualTo(schedule.calculatedExpirationSecond());
        assertThat(expected.resolutionTime()).isEqualTo(schedule.resolutionTime());
        assertThat(expected.originalCreateTransaction()).isEqualTo(schedule.originalCreateTransaction());
        assertThat(expected.scheduledTransaction()).isNotNull();
        assertThat(schedule.scheduledTransaction()).isNotNull();
        final byte[] expectedBytes = PbjConverter.asBytes(protobufCodec, expected.scheduledTransaction());
        final byte[] actualBytes = PbjConverter.asBytes(protobufCodec, schedule.scheduledTransaction());
        assertThat(expectedBytes).containsExactly(actualBytes);
    }

    @Test
    void verifyReverseTranslationOfLookupById() throws InvalidKeyException {
        BDDMockito.given(states.<ScheduleID, Schedule>get("SCHEDULES_BY_ID")).willReturn(schedulesById);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(getValidSchedule());

        final ScheduleVirtualValue scheduleVirtualValue =
                ScheduleServiceStateTranslator.pbjToState(testScheduleID, scheduleStore);
        assertThat(scheduleVirtualValue).isNotNull();
        final List<byte[]> expectedSignatories = scheduleVirtualValue.signatories();
        final List<byte[]> actualSignatories = subject.signatories();
        assertThat(actualSignatories).isNotNull();
        assertThat(expectedSignatories).containsExactlyInAnyOrderElementsOf(actualSignatories);
        assertThat(scheduleVirtualValue.memo()).isEqualTo(subject.memo());
        assertThat(scheduleVirtualValue.isDeleted()).isEqualTo(subject.isDeleted());
        assertThat(scheduleVirtualValue.isExecuted()).isEqualTo(subject.isExecuted());
        assertThat(scheduleVirtualValue.adminKey()).isEqualTo(subject.adminKey());
        assertThat(scheduleVirtualValue.waitForExpiryProvided()).isEqualTo(subject.waitForExpiryProvided());
        assertThat(scheduleVirtualValue.payer()).isEqualTo(subject.payer());
        assertThat(scheduleVirtualValue.schedulingAccount()).isEqualTo(subject.schedulingAccount());
        assertThat(scheduleVirtualValue.schedulingTXValidStart()).isEqualTo(subject.schedulingTXValidStart());
        assertThat(scheduleVirtualValue.expirationTimeProvided()).isEqualTo(subject.expirationTimeProvided());
        assertThat(scheduleVirtualValue.calculatedExpirationTime()).isEqualTo(subject.calculatedExpirationTime());
        assertThat(scheduleVirtualValue.getResolutionTime()).isEqualTo(subject.getResolutionTime());
        assertThat(scheduleVirtualValue.ordinaryViewOfScheduledTxn()).isEqualTo(subject.ordinaryViewOfScheduledTxn());
        assertThat(scheduleVirtualValue.scheduledTxn()).isEqualTo(subject.scheduledTxn());
        assertThat(scheduleVirtualValue.bodyBytes()).containsExactly(subject.bodyBytes());
    }

    private Schedule getValidSchedule() {
        testConfig = HederaTestConfigBuilder.create().getOrCreateConfig();
        scheduled = createSampleScheduled();
        originalCreateTransaction = originalCreateTransaction(scheduled, payerAccountId, adminKey);

        return Schedule.newBuilder()
                .deleted(false)
                .executed(false)
                .waitForExpiry(true)
                .memo(memo)
                .scheduleId(testScheduleID)
                .schedulerAccountId(scheduler)
                .payerAccountId(payerAccountId)
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
                        .deleteAccountID(AccountID.newBuilder().accountNum(2))
                        .transferAccountID(AccountID.newBuilder().accountNum(75231)))
                .transactionFee(FEE)
                .memo(SCHEDULED_TRANSACTION_MEMO)
                .build();
        return scheduledTxn;
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
                .setMemo(SCHEDULED_TRANSACTION_MEMO)
                .setCryptoDelete(com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody.newBuilder()
                        .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
                        .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
                .build();
    }
}
