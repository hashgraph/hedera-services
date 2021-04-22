package com.hedera.services.txns.schedule;

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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static com.hedera.services.txns.schedule.SigMapScheduleClassifierTest.pretendKeyStartingWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class ScheduleSignTransitionLogicTest {
    private ScheduleStore store;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;
    final TransactionID scheduledTxnId = TransactionID.newBuilder()
            .setAccountID(IdUtils.asAccount("0.0.2"))
            .setScheduled(true)
            .build();
    private JKey payerKey = new JEd25519Key(pretendKeyStartingWith("payer"));
    private SigMapScheduleClassifier classifier;
    private Optional<List<JKey>> validScheduleKeys = Optional.of(
            List.of(new JEd25519Key(pretendKeyStartingWith("scheduled"))));

    private TransactionBody scheduleSignTxn;

    InHandleActivationHelper activationHelper;
    private SignatureMap sigMap;
    private SignatoryUtils.ScheduledSigningsWitness replSigningWitness;
    private ScheduleReadyForExecution.ExecutionProcessor executor;

    private ScheduleSignTransitionLogic subject;
    private ScheduleID scheduleId = IdUtils.asSchedule("1.2.3");
    private MerkleSchedule schedule;

    @BeforeEach
    private void setup() throws InvalidProtocolBufferException {
        store = mock(ScheduleStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        executor = mock(ScheduleReadyForExecution.ExecutionProcessor.class);
        activationHelper = mock(InHandleActivationHelper.class);
        txnCtx = mock(TransactionContext.class);
        replSigningWitness = mock(SignatoryUtils.ScheduledSigningsWitness.class);
        classifier = mock(SigMapScheduleClassifier.class);
        schedule = mock(MerkleSchedule.class);
        given(txnCtx.activePayerKey()).willReturn(payerKey);

        given(replSigningWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper))
                .willReturn(Pair.of(OK, true));
        given(executor.doProcess(scheduleId)).willReturn(OK);

        subject = new ScheduleSignTransitionLogic(store, txnCtx, activationHelper);

        subject.replSigningsWitness = replSigningWitness;
        subject.executor = executor;
        subject.classifier = classifier;
    }

    @Test
    public void hasCorrectApplicability() {
        givenValidTxnCtx();
        // expect:
        assertTrue(subject.applicability().test(scheduleSignTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    public void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        // and:
        given(replSigningWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper))
                .willThrow(IllegalArgumentException.class);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    public void failsOnInvalidScheduleId() {
        givenCtx(true);
        // expect:
        assertEquals(INVALID_SCHEDULE_ID, subject.validate(scheduleSignTxn));
    }

    @Test
    public void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    @Test
    public void abortsImmediatelyIfScheduleIsExecuted() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.isExecuted()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
		verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).doProcess(scheduleId);
        verify(txnCtx).setStatus(SCHEDULE_ALREADY_EXECUTED);
    }

    @Test
    public void abortsImmediatelyIfScheduleIsDeleted() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.isDeleted()).willReturn(true);

        // when:
        subject.doStateTransition();

        // and:
        verifyNoInteractions(classifier);
        verify(txnCtx, never()).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).doProcess(scheduleId);
        verify(txnCtx).setStatus(SCHEDULE_ALREADY_DELETED);
    }

    @Test
    public void followsHappyPath() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);

        // when:
        subject.doStateTransition();

        // and:
		verify(txnCtx).setScheduledTxnId(scheduledTxnId);
		verify(executor).doProcess(scheduleId);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void execsOnlyIfReady() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(schedule.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(replSigningWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper))
                .willReturn(Pair.of(OK, false));

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setStatus(SUCCESS);
        // and:
        verify(executor, never()).doProcess(any());
    }

    @Test
    public void shortCircuitsOnNonOkSigningOutcome() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(store.get(scheduleId)).willReturn(schedule);
        given(replSigningWitness.observeInScope(scheduleId, store, validScheduleKeys, activationHelper))
                .willReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));

        // when:
        subject.doStateTransition();

        // and:
        verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
        // and:
        verify(executor, never()).doProcess(any());
    }

    @Test
    public void rejectsInvalidScheduleId() {
        givenCtx(true);
        assertEquals(INVALID_SCHEDULE_ID, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false);
    }

    private void givenCtx(boolean invalidScheduleId) {
        sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                        .build())
                .build();
        given(accessor.getSigMap()).willReturn(sigMap);
        given(classifier.validScheduleKeys(
                eq(List.of(payerKey)),
                eq(sigMap),
                any(),
                any())).willReturn(validScheduleKeys);

        var builder = TransactionBody.newBuilder();
        var scheduleSign = ScheduleSignTransactionBody.newBuilder()
                .setScheduleID(scheduleId);
        if (invalidScheduleId) {
            scheduleSign.clearScheduleID();
        }

        builder.setScheduleSign(scheduleSign);

        scheduleSignTxn = builder.build();

        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}
