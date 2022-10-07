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
package com.hedera.services.txns.schedule;

import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleSignTransitionLogicTest {
    private ScheduleStore store;
    private SignedTxnAccessor accessor;
    private TransactionContext txnCtx;
    final TransactionID scheduledTxnId =
            TransactionID.newBuilder()
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .setScheduled(true)
                    .build();
    private JKey payerKey = new JEd25519Key(pretendKeyStartingWith("payer"));
    private SigMapScheduleClassifier classifier;
    private Optional<List<JKey>> validScheduleKeys =
            Optional.of(List.of(new JEd25519Key(pretendKeyStartingWith("scheduled"))));

    private TransactionBody scheduleSignTxn;

    InHandleActivationHelper activationHelper;
    private SignatureMap sigMap;
    private SignatoryUtils.ScheduledSigningsWitness replSigningWitness;
    private ScheduleExecutor executor;

    private ScheduleSignTransitionLogic subject;
    private ScheduleID scheduleId = IdUtils.asSchedule("1.2.3");
    private ScheduleVirtualValue schedule;
    private GlobalDynamicProperties properties;
    private ScheduleProcessing scheduleProcessing;

    @BeforeEach
    void setup() throws InvalidProtocolBufferException {
        store = mock(ScheduleStore.class);
        accessor = mock(SignedTxnAccessor.class);
        executor = mock(ScheduleExecutor.class);
        activationHelper = mock(InHandleActivationHelper.class);
        txnCtx = mock(TransactionContext.class);
        replSigningWitness = mock(SignatoryUtils.ScheduledSigningsWitness.class);
        classifier = mock(SigMapScheduleClassifier.class);
        schedule = mock(ScheduleVirtualValue.class);
        properties = mock(GlobalDynamicProperties.class);
        scheduleProcessing = mock(ScheduleProcessing.class);
        given(txnCtx.activePayerKey()).willReturn(payerKey);

        given(
                        replSigningWitness.observeInScope(
                                scheduleId, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(OK, true));
        given(executor.processImmediateExecution(scheduleId, store, txnCtx)).willReturn(OK);

        subject =
                new ScheduleSignTransitionLogic(
                        properties, store, txnCtx, activationHelper, executor, scheduleProcessing);

        subject.replSigningsWitness = replSigningWitness;
        subject.classifier = classifier;
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();
        // expect:
        assertTrue(subject.applicability().test(scheduleSignTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        // and:
        given(properties.schedulingLongTermEnabled()).willThrow(IllegalArgumentException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void validateFailsOnInvalidScheduleId() {
        givenCtx(true);
        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleSignTxn));
    }

    @Test
    void validateFailsOnMissingSchedule() {
        givenValidTxnCtx();
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleSignTxn));
    }

    @Test
    void validateHappyPath() {
        givenValidTxnCtx();
        // expect:
        assertEquals(OK, subject.validate(scheduleSignTxn));
    }

    @Test
    void validateHappyPathLongTermEnabled() {
        givenValidTxnCtx();
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(store.getNoError(scheduleId)).willReturn(schedule);
        // expect:
        assertEquals(OK, subject.validate(scheduleSignTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(scheduleSignTxn));
    }

    @Test
    void abortsImmediatelyIfScheduleIsExecuted() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.isExecuted()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SCHEDULE_ALREADY_EXECUTED);
    }

    @Test
    void abortsImmediatelyIfScheduleIsNull() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.getNoError(scheduleId)).willReturn(null);
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(schedule.isExecuted()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(INVALID_SCHEDULE_ID);
    }

    @Test
    void abortsImmediatelyIfScheduleIsDeleted() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.isDeleted()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SCHEDULE_ALREADY_DELETED);
    }

    @Test
    void abortsImmediatelyIfAfterExpiration() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.getNoError(scheduleId)).willReturn(schedule);
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(schedule.calculatedExpirationTime())
                .willReturn(RichInstant.fromJava(Instant.EPOCH.minusNanos(1)));

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SCHEDULE_PENDING_EXPIRATION);
    }

    @Test
    void abortsImmediatelyIfAfterExpirationLongTermDisabled()
            throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(properties.schedulingLongTermEnabled()).willReturn(false);
        given(schedule.calculatedExpirationTime())
                .willReturn(RichInstant.fromJava(Instant.EPOCH.minusNanos(1)));

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(INVALID_SCHEDULE_ID);
    }

    @Test
    void followsHappyPathIfLongTermEnabled() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(store.getNoError(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(properties.schedulingLongTermEnabled()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(executor).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void doesNotProcessIfLongTermEnabledAndWaitForExpiry() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(store.getNoError(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(schedule.calculatedWaitForExpiry()).willReturn(true);
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(
                        replSigningWitness.observeInScope(
                                scheduleId, store, validScheduleKeys, activationHelper, true))
                .willReturn(Pair.of(OK, false));

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SUCCESS);
        verify(replSigningWitness, never()).observeInScope(any(), any(), any(), any(), eq(false));
    }

    @Test
    void followsHappyPathIfLongTermDisabledAndWaitForExpiry()
            throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(store.getNoError(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(schedule.calculatedWaitForExpiry()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(executor).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void followsHappyPath() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(executor).processImmediateExecution(scheduleId, store, txnCtx);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    void execsOnlyIfReady() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(
                        replSigningWitness.observeInScope(
                                scheduleId, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(OK, false));

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setStatus(SUCCESS);
        // and:
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
    }

    @Test
    void shortCircuitsOnNonOkSigningOutcome() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(
                        replSigningWitness.observeInScope(
                                scheduleId, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
        // and:
        verify(executor, never()).processImmediateExecution(scheduleId, store, txnCtx);
    }

    @Test
    void rejectsInvalidScheduleId() {
        givenCtx(true);
        assertEquals(INVALID_SCHEDULE_ID, subject.semanticCheck().apply(scheduleSignTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false);
    }

    private void givenCtx(boolean invalidScheduleId) {
        sigMap =
                SignatureMap.newBuilder()
                        .addSigPair(
                                SignaturePair.newBuilder()
                                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                                        .build())
                        .build();
        given(accessor.getSigMap()).willReturn(sigMap);
        given(classifier.validScheduleKeys(eq(List.of(payerKey)), eq(sigMap), any(), any()))
                .willReturn(validScheduleKeys);

        var builder = TransactionBody.newBuilder();
        var scheduleSign = ScheduleSignTransactionBody.newBuilder().setScheduleID(scheduleId);
        if (invalidScheduleId) {
            scheduleSign.clearScheduleID();
        }

        builder.setScheduleSign(scheduleSign);

        scheduleSignTxn = builder.build();

        given(txnCtx.consensusTime()).willReturn(Instant.EPOCH);
        given(schedule.calculatedExpirationTime()).willReturn(RichInstant.fromJava(Instant.EPOCH));

        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
