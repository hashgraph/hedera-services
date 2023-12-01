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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.schedule.impl.ScheduleTestBase;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.Codec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceStateTranslatorTest extends ScheduleTestBase {
    private final byte[] payerKeyBytes = requireNonNull(payerKey.ed25519()).toByteArray();
    private final byte[] schedulerKeyBytes =
            requireNonNull(schedulerKey.ed25519()).toByteArray();
    private final byte[] adminKeyBytes = requireNonNull(adminKey.ed25519()).toByteArray();

    // Non-Mock objects that require constructor initialization to avoid unnecessary statics
    private EntityNumVirtualKey protoKey;
    private byte[] protoBodyBytes;

    private List<Key> signatoryList;
    private Schedule testValue;

    // Non-Mock objects, but may contain or reference mock objects.
    private ScheduleVirtualValue subject;

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
        protoKey = new EntityNumVirtualKey(scheduleInState.scheduleId().scheduleNum());
        protoBodyBytes = getBodyBytes(scheduleInState);
        subject = ScheduleVirtualValue.from(protoBodyBytes, expirationTime.seconds());
        subject.setKey(protoKey);
        subject.witnessValidSignature(payerKeyBytes);
        subject.witnessValidSignature(schedulerKeyBytes);
        subject.witnessValidSignature(adminKeyBytes);
        signatoryList = List.of(adminKey, schedulerKey, payerKey);
        testValue = scheduleInState.copyBuilder().signatories(signatoryList).build();
        // make sure our test value is what is in state
        writableSchedules.put(testValue);
        commit(writableById);
        reset(writableById);
    }

    private byte[] getBodyBytes(final Schedule scheduleInState) {
        final TransactionBody originalCreateBytes = requireNonNull(scheduleInState.originalCreateTransaction());
        return PbjConverter.asBytes(TransactionBody.PROTOBUF, originalCreateBytes);
    }

    @Test
    void verifyTypicalForwardTranslation() throws IOException {
        assertThat(testValue.memo()).isEqualTo(subject.memo().get());
        final ScheduleVirtualValue actual = ScheduleServiceStateTranslator.pbjToState(testValue);
        final ScheduleVirtualValue expected = subject;

        assertThat(actual).isNotNull();
        final List<byte[]> actualSignatories = actual.signatories();
        final List<byte[]> expectedSignatories = expected.signatories();
        assertThat(actualSignatories).isNotNull().containsExactlyInAnyOrderElementsOf(expectedSignatories);
        assertThat(actual.memo()).isEqualTo(expected.memo());
        assertThat(actual.isDeleted()).isEqualTo(expected.isDeleted());
        assertThat(actual.isExecuted()).isEqualTo(expected.isExecuted());
        assertThat(actual.adminKey()).isEqualTo(expected.adminKey());
        assertThat(actual.waitForExpiryProvided()).isEqualTo(expected.waitForExpiryProvided());
        assertThat(actual.payer()).isEqualTo(expected.payer());
        assertThat(actual.schedulingAccount()).isEqualTo(expected.schedulingAccount());
        assertThat(actual.schedulingTXValidStart()).isEqualTo(expected.schedulingTXValidStart());
        assertThat(actual.expirationTimeProvided()).isEqualTo(expected.expirationTimeProvided());
        assertThat(actual.calculatedExpirationTime()).isEqualTo(expected.calculatedExpirationTime());
        assertThat(actual.getResolutionTime()).isEqualTo(expected.getResolutionTime());
        assertThat(actual.ordinaryViewOfScheduledTxn()).isEqualTo(expected.ordinaryViewOfScheduledTxn());
        assertThat(actual.scheduledTxn()).isEqualTo(expected.scheduledTxn());
        assertThat(actual.bodyBytes()).containsExactly(expected.bodyBytes());
    }

    @Test
    void verifyTypicalReverseTranslation() throws IOException {
        final Codec<SchedulableTransactionBody> protobufCodec = SchedulableTransactionBody.PROTOBUF;
        final Schedule actual = ScheduleServiceStateTranslator.convertScheduleVirtualValueToSchedule(subject);
        // original create has different values from the schedule for test purposes, but SVV uses original create only.
        // Here we adjust the expected schedule to match the original schedule create values.
        final Schedule.Builder expectedBuilder = testValue.copyBuilder().payerAccountId(scheduler);
        final Schedule expected = expectedBuilder.providedExpirationSecond(0L).build();

        assertThat(actual).isNotNull();
        final List<Key> expectedSignatories = expected.signatories();
        final List<Key> actualSignatories = actual.signatories();
        assertThat(actualSignatories).isNotNull().containsExactlyInAnyOrderElementsOf(expectedSignatories);
        assertThat(actual.memo()).isEqualTo(expected.memo());
        assertThat(actual.deleted()).isEqualTo(expected.deleted());
        assertThat(actual.executed()).isEqualTo(expected.executed());
        assertThat(actual.adminKey()).isEqualTo(expected.adminKey());
        assertThat(actual.waitForExpiry()).isEqualTo(expected.waitForExpiry());
        assertThat(actual.payerAccountId()).isEqualTo(expected.payerAccountId());
        assertThat(actual.schedulerAccountId()).isEqualTo(expected.schedulerAccountId());
        assertThat(actual.scheduleValidStart()).isEqualTo(expected.scheduleValidStart());
        assertThat(actual.providedExpirationSecond()).isEqualTo(expected.providedExpirationSecond());
        assertThat(actual.calculatedExpirationSecond()).isEqualTo(expected.calculatedExpirationSecond());
        assertThat(actual.resolutionTime()).isEqualTo(expected.resolutionTime());
        assertThat(actual.originalCreateTransaction()).isEqualTo(expected.originalCreateTransaction());
        assertThat(expected.scheduledTransaction()).isNotNull();
        assertThat(actual.scheduledTransaction()).isNotNull();
        final byte[] expectedBytes = PbjConverter.asBytes(protobufCodec, expected.scheduledTransaction());
        final byte[] actualBytes = PbjConverter.asBytes(protobufCodec, actual.scheduledTransaction());
        assertThat(actualBytes).containsExactly(expectedBytes);
    }

    @Test
    void verifyReverseTranslationOfLookupById() throws InvalidKeyException {
        final ScheduleVirtualValue actual = ScheduleServiceStateTranslator.pbjToState(testScheduleID, scheduleStore);
        final ScheduleVirtualValue expected = subject;
        assertThat(actual).isNotNull();
        final List<byte[]> expectedSignatories = actual.signatories();
        final List<byte[]> actualSignatories = expected.signatories();
        assertThat(actualSignatories).isNotNull().containsExactlyInAnyOrderElementsOf(expectedSignatories);
        assertThat(actual.memo()).isEqualTo(expected.memo());
        assertThat(actual.isDeleted()).isEqualTo(expected.isDeleted());
        assertThat(actual.isExecuted()).isEqualTo(expected.isExecuted());
        assertThat(actual.adminKey()).isEqualTo(expected.adminKey());
        assertThat(actual.waitForExpiryProvided()).isEqualTo(expected.waitForExpiryProvided());
        assertThat(actual.payer()).isEqualTo(expected.payer());
        assertThat(actual.schedulingAccount()).isEqualTo(expected.schedulingAccount());
        assertThat(actual.schedulingTXValidStart()).isEqualTo(expected.schedulingTXValidStart());
        assertThat(actual.expirationTimeProvided()).isEqualTo(expected.expirationTimeProvided());
        assertThat(actual.calculatedExpirationTime()).isEqualTo(expected.calculatedExpirationTime());
        assertThat(actual.getResolutionTime()).isEqualTo(expected.getResolutionTime());
        assertThat(actual.ordinaryViewOfScheduledTxn()).isEqualTo(expected.ordinaryViewOfScheduledTxn());
        assertThat(actual.scheduledTxn()).isEqualTo(expected.scheduledTxn());
        assertThat(actual.bodyBytes()).containsExactly(expected.bodyBytes());
    }

    // TODO: Create a test with deleted and executed schedules.

}
