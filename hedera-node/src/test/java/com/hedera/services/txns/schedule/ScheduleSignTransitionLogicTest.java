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
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignaturePair;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SOME_SIGNATURES_WERE_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static junit.framework.TestCase.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@RunWith(JUnitPlatform.class)
public class ScheduleSignTransitionLogicTest {
    private ScheduleStore store;
    private PlatformTxnAccessor accessor;
    private TransactionContext txnCtx;
    private byte[] transactionBody = TransactionBody.newBuilder()
            .setMemo("Just this")
            .build()
            .toByteArray();

    private TransactionBody scheduleSignTxn;

    InHandleActivationHelper activationHelper;
    private SignatureMap.Builder sigMap;
    private SignatoryUtils.SigningsWitness signingWitness;
    private ScheduleReadyForExecution.ExecutionProcessor executor;

    private ScheduleSignTransitionLogic subject;
    private ScheduleID schedule = IdUtils.asSchedule("1.2.3");

    @BeforeEach
    private void setup() throws InvalidProtocolBufferException {
        store = mock(ScheduleStore.class);
        accessor = mock(PlatformTxnAccessor.class);
        executor = mock(ScheduleReadyForExecution.ExecutionProcessor.class);
        activationHelper = mock(InHandleActivationHelper.class);
        signingWitness = mock(SignatoryUtils.SigningsWitness.class);
        txnCtx = mock(TransactionContext.class);

        given(signingWitness.observeInScope(1, schedule, store, activationHelper))
                .willReturn(Pair.of(OK, true));
        given(executor.doProcess(schedule)).willReturn(OK);

        subject = new ScheduleSignTransitionLogic(store, txnCtx, activationHelper);

        subject.signingsWitness = signingWitness;
        subject.executor = executor;
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
        given(signingWitness.observeInScope(1, schedule, store, activationHelper))
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
        given(store.exists(schedule)).willReturn(true);

        // expect:
        assertEquals(OK, subject.syntaxCheck().apply(scheduleSignTxn));
    }

    @Test
    public void followsHappyPath() throws InvalidProtocolBufferException {
        givenValidTxnCtx();

        // when:
        subject.doStateTransition();

        // and:
		verify(executor).doProcess(schedule);
        verify(txnCtx).setStatus(SUCCESS);
    }

    @Test
    public void execsOnlyIfReady() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(signingWitness.observeInScope(1, schedule, store, activationHelper))
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
        given(signingWitness.observeInScope(1, schedule, store, activationHelper))
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
        this.sigMap = SignatureMap.newBuilder().addSigPair(
                SignaturePair.newBuilder()
                        .setPubKeyPrefix(ByteString.copyFromUtf8("a"))
                        .build());

        var builder = TransactionBody.newBuilder();
        var scheduleSign = ScheduleSignTransactionBody.newBuilder()
                .setSigMap(sigMap)
                .setScheduleID(schedule);
        if (invalidScheduleId) {
            scheduleSign.clearScheduleID();
        }

        builder.setScheduleSign(scheduleSign);

        scheduleSignTxn = builder.build();

        given(accessor.getTxn()).willReturn(scheduleSignTxn);
        given(txnCtx.accessor()).willReturn(accessor);
    }
}