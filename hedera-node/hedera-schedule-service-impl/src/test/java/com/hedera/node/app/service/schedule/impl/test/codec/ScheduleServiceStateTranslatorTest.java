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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ScheduleServiceStateTranslatorTest {
    // spotless mangles this section randomly, due to incorrect wrapping rules
    // spotless:off
    // A few random values for fake ed25519 test keys
    protected static final String PAYER_KEY_HEX = "badcadfaddad2bedfedbeef959feedbeadcafecadecedebeed4acedecada5ada";
    protected static final String SCHEDULER_KEY_HEX =
            "feedbeadcafe8675309bafedfacecaeddeedcedebede4adaacecab2badcadfad";
    // This one is a perfect 10.
    protected static final String ADMIN_KEY_HEX = "0000000000191561942608236107294793378084303638130997321548169216";
    protected final ScheduleID testScheduleID =
            ScheduleID.newBuilder().scheduleNum(1001L).build();
    protected final long testExpiry = 123L;
    protected final String testStrEquality = "06abd7245b5e40473719948f7dee8120de725e41aa5a72cde8001f72c940ca7d";
    protected AccountID adminAccount =
            AccountID.newBuilder().accountNum(626068L).build();
    protected static Key adminKey =
            Key.newBuilder().ed25519(Bytes.fromHex(ADMIN_KEY_HEX)).build();
    protected AccountID scheduler = AccountID.newBuilder().accountNum(100L).build();
    protected Key schedulerKey =
            Key.newBuilder().ed25519(Bytes.fromHex(SCHEDULER_KEY_HEX)).build();
    protected AccountID payer = AccountID.newBuilder().accountNum(2001L).build();
    protected Key payerKey =
            Key.newBuilder().ed25519(Bytes.fromHex(PAYER_KEY_HEX)).build();
    protected Timestamp testValidStart =
            Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected Timestamp expirationTime =
            Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected Timestamp calculatedExpirationTime =
            Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected Timestamp resolutionTime =
            Timestamp.newBuilder().seconds(2281580449L).nanos(0).build();
    protected List<Key> keyList = Arrays.asList(adminKey, schedulerKey, payerKey);

    protected String memo = "Test";
    protected Schedule scheduleInState;

    // Non-Mock objects, but may contain or reference mock objects.
    protected ReadableScheduleStoreImpl scheduleStore;
    protected Configuration testConfig;
    protected SchedulableTransactionBody scheduled;
    protected TransactionBody originalCreateTransaction;

    protected final byte[] fpk = payerKey.ed25519().toByteArray();
    protected final byte[] spk = schedulerKey.ed25519().toByteArray();
    protected final byte[] tpk = adminKey.ed25519().toByteArray();
    protected final long expiry = 2281580449L;
    protected static final RichInstant providedExpiry = new RichInstant(2281580449L, 0);
    protected static final boolean waitForExpiry = true;
    protected static final String entityMemo = "Test";
    protected static final EntityId schedulingAccount = new EntityId(0, 0, 100L);
    protected final Instant resolutionTimeVirtual = Instant.ofEpochSecond(2281580449L);
    protected final com.hederahashgraph.api.proto.java.Timestamp grpcResolutionTime =
            RichInstant.fromJava(resolutionTimeVirtual).toGrpc();
    protected static final RichInstant schedulingTXValidStart = new RichInstant(2281580449L, 0);
    protected static final JKey adminKeyVirtual = (com.hedera.node.app.service.mono.legacy.core.jproto.JKey)
            com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbjKey(adminKey)
                    .orElse(null);
    protected static final EntityId payerVirtual = new EntityId(0, 0, 2001L);

    protected List<byte[]> signatories;
    protected ScheduleVirtualValue subject;
    private static final long FEE = 123L;

    private static final String SCHEDULED_TXN_MEMO = "Wait for me!";
    public static final com.hederahashgraph.api.proto.java.SchedulableTransactionBody scheduledTxn =
            com.hederahashgraph.api.proto.java.SchedulableTransactionBody.newBuilder()
                    .setTransactionFee(FEE)
                    .setMemo(SCHEDULED_TXN_MEMO)
                    .setCryptoDelete(com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody.newBuilder()
                            .setDeleteAccountID(IdUtils.asAccount("0.0.2"))
                            .setTransferAccountID(IdUtils.asAccount("0.0.75231")))
                    .build();

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableStates states;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<ScheduleID, Schedule> schedulesById;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<Long, List<Schedule>> schedulesByLong;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ReadableKVStateBase<String, List<Schedule>> schedulesByString;

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

        assertTrue(scheduleVirtualValue != null);
        assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        assertThat(scheduleVirtualValue.signatories()).containsAll(subject.signatories());
        assertEquals(scheduleVirtualValue.memo(), subject.memo());
        assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        assertEquals(scheduleVirtualValue.payer(), subject.payer());
        assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        assertEquals(subject.ordinaryViewOfScheduledTxn(), scheduleVirtualValue.ordinaryViewOfScheduledTxn());
        assertEquals(subject.scheduledTxn(), scheduleVirtualValue.scheduledTxn());
        assertArrayEquals(subject.bodyBytes(), scheduleVirtualValue.bodyBytes());
    }

    @Test
    void createScheduleFromScheduleVirtualValue() throws IOException {

        final Schedule schedule = ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(subject);
        Schedule expected = getValidSchedule();

        assertTrue(schedule != null);
        assertEquals(expected.signatories().size(), schedule.signatories().size());
        assertThat(expected.signatories()).containsAll(schedule.signatories());
        assertEquals(expected.memo(), schedule.memo());
        assertEquals(expected.deleted(), schedule.deleted());
        assertEquals(expected.executed(), schedule.executed());
        assertEquals(expected.adminKey(), schedule.adminKey());
        assertEquals(expected.waitForExpiry(), schedule.waitForExpiry());
        assertEquals(expected.payerAccount(), schedule.payerAccount());
        assertEquals(expected.schedulerAccount(), schedule.schedulerAccount());
        assertEquals(expected.scheduleValidStart(), schedule.scheduleValidStart());
        assertEquals(expected.expirationTimeProvided(), schedule.expirationTimeProvided());
        assertEquals(expected.calculatedExpirationTime(), schedule.calculatedExpirationTime());
        assertEquals(expected.resolutionTime(), schedule.resolutionTime());
        assertEquals(expected.originalCreateTransaction(), schedule.originalCreateTransaction());
        assertArrayEquals(
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
        assertTrue(scheduleVirtualValue != null);
        assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        assertThat(scheduleVirtualValue.signatories()).containsAll(subject.signatories());
        assertEquals(scheduleVirtualValue.memo(), subject.memo());
        assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        assertEquals(scheduleVirtualValue.payer(), subject.payer());
        assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        assertEquals(scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
    }

    @Test
    void createScheduleVirtualValueFromScheduleUsingReadableScheduleStoreByLong() throws InvalidKeyException {
        BDDMockito.given(states.<Long, List<Schedule>>get("SCHEDULES_BY_EXPIRY_SEC"))
                .willReturn(schedulesByLong);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesByLong.get(testExpiry)).willReturn(List.of(getValidSchedule()));

        final List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue>
                listScheduleVirtualValue = ScheduleServiceStateTranslator.pbjToState(testExpiry, scheduleStore);
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                listScheduleVirtualValue.get(0);
        assertTrue(scheduleVirtualValue != null);
        assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        assertThat(scheduleVirtualValue.signatories()).containsAll(subject.signatories());
        assertEquals(scheduleVirtualValue.memo(), subject.memo());
        assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        assertEquals(scheduleVirtualValue.payer(), subject.payer());
        assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        assertEquals(scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
    }

    @Test
    void createScheduleVirtualValueFromScheduleUsingReadableScheduleStoreBySchedule() throws InvalidKeyException {
        Schedule schedule = getValidSchedule();
        BDDMockito.given(states.<String, List<Schedule>>get("SCHEDULES_BY_EQUALITY"))
                .willReturn(schedulesByString);
        scheduleStore = new ReadableScheduleStoreImpl(states);
        BDDMockito.given(schedulesByString.get(testStrEquality)).willReturn(List.of(schedule));

        final List<com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue>
                listScheduleVirtualValue = ScheduleServiceStateTranslator.pbjToState(schedule, scheduleStore);
        com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue scheduleVirtualValue =
                listScheduleVirtualValue.get(0);
        assertTrue(scheduleVirtualValue != null);
        assertEquals(
                scheduleVirtualValue.signatories().size(), subject.signatories().size());
        assertThat(scheduleVirtualValue.signatories()).containsAll(subject.signatories());
        assertEquals(scheduleVirtualValue.memo(), subject.memo());
        assertEquals(scheduleVirtualValue.isDeleted(), subject.isDeleted());
        assertEquals(scheduleVirtualValue.isExecuted(), subject.isExecuted());
        assertEquals(scheduleVirtualValue.adminKey(), subject.adminKey());
        assertEquals(scheduleVirtualValue.waitForExpiryProvided(), subject.waitForExpiryProvided());
        assertEquals(scheduleVirtualValue.payer(), subject.payer());
        assertEquals(scheduleVirtualValue.schedulingAccount(), subject.schedulingAccount());
        assertEquals(scheduleVirtualValue.schedulingTXValidStart(), subject.schedulingTXValidStart());
        assertEquals(scheduleVirtualValue.expirationTimeProvided(), subject.expirationTimeProvided());
        assertEquals(scheduleVirtualValue.calculatedExpirationTime(), subject.calculatedExpirationTime());
        assertEquals(scheduleVirtualValue.getResolutionTime(), subject.getResolutionTime());
        assertEquals(scheduleVirtualValue.ordinaryViewOfScheduledTxn(), subject.ordinaryViewOfScheduledTxn());
        assertEquals(scheduleVirtualValue.scheduledTxn(), subject.scheduledTxn());
        assertArrayEquals(scheduleVirtualValue.bodyBytes(), subject.bodyBytes());
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
                .id(testScheduleID)
                .schedulerAccount(scheduler)
                .payerAccount(payer)
                .adminKey(adminKey)
                .scheduleValidStart(testValidStart)
                .expirationTimeProvided(expirationTime)
                .calculatedExpirationTime(calculatedExpirationTime)
                .scheduledTransaction(scheduled)
                .originalCreateTransaction(originalCreateTransaction)
                .signatories(keyList)
                .build();
    }

    private static SchedulableTransactionBody createSampleScheduled() {
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

    protected TransactionBody originalCreateTransaction(
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

    private static final com.hederahashgraph.api.proto.java.TransactionID parentTxnId =
            com.hederahashgraph.api.proto.java.TransactionID.newBuilder()
                    .setTransactionValidStart(MiscUtils.asTimestamp(schedulingTXValidStart.toJava()))
                    .setNonce(4444)
                    .setAccountID(schedulingAccount.toGrpcAccountId())
                    .build();

    private static final com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody creation =
            com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody.newBuilder()
                    .setAdminKey(MiscUtils.asKeyUnchecked(adminKeyVirtual))
                    .setPayerAccountID(payerVirtual.toGrpcAccountId())
                    .setExpirationTime(providedExpiry.toGrpc())
                    .setWaitForExpiry(waitForExpiry)
                    .setMemo(entityMemo)
                    .setScheduledTransactionBody(scheduledTxn)
                    .build();

    private static final com.hederahashgraph.api.proto.java.TransactionBody parentTxn =
            com.hederahashgraph.api.proto.java.TransactionBody.newBuilder()
                    .setTransactionID(parentTxnId)
                    .setScheduleCreate(creation)
                    .build();

    private static final byte[] bodyBytes = parentTxn.toByteArray();
}
