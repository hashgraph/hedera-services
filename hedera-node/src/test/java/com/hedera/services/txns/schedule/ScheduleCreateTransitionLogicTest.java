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
import static com.hedera.services.utils.MiscUtils.asUsableFcKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_NEW_VALID_SIGNATURES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED;
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.keys.InHandleActivationHelper;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleCreateTransitionLogicTest {
    private static final long thisSecond = 1_234_567L;
    private static final Instant now = Instant.ofEpochSecond(thisSecond);
    private static final byte[] bodyBytes =
            TransactionBody.newBuilder().setMemo("Just this").build().toByteArray();
    private static final TransactionID scheduledTxnId =
            TransactionID.newBuilder()
                    .setAccountID(IdUtils.asAccount("0.0.2"))
                    .setScheduled(true)
                    .build();

    private static final Key key = SignedTxnFactory.DEFAULT_PAYER_KT.asKey();
    private static final Key invalidKey = Key.getDefaultInstance();

    private static final AccountID payer = IdUtils.asAccount("1.2.3");
    private static final ScheduleID schedule = IdUtils.asSchedule("2.4.6");
    private static final String entityMemo = "some cool memo?";
    private static final String innerMemo = "Strictly business now";

    private static final TransactionID txnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(
                            Timestamp.newBuilder()
                                    .setSeconds(now.getEpochSecond())
                                    .setNanos(now.getNano())
                                    .build())
                    .build();

    private static final JKey payerKey = new JEd25519Key(pretendKeyStartingWith("payer"));
    private static final Optional<JKey> jAdminKey = asUsableFcKey(key);
    private static final Optional<List<JKey>> validScheduleKeys =
            Optional.of(List.of(new JEd25519Key(pretendKeyStartingWith("scheduled"))));
    private static final SignatureMap sigMap = SigMapScheduleClassifierTest.sigMap;

    private boolean adminKeyActuallySkipped = false;
    private boolean invalidAdminKeyIsSentinelKeyList = false;

    private UsageLimits usageLimits;
    private OptionValidator validator;
    private ScheduleStore store;
    private ScheduleVirtualValue scheduleValue;
    private SignedTxnAccessor accessor;
    private TransactionContext txnCtx;
    private SignatoryUtils.ScheduledSigningsWitness replSigningWitness;
    private ScheduleExecutor executor;
    private SigImpactHistorian sigImpactHistorian;
    private TransactionBody scheduleCreateTxn;
    private InHandleActivationHelper activationHelper;
    private SigMapScheduleClassifier classifier;
    private GlobalDynamicProperties properties;
    private ScheduleProcessing scheduleProcessing;

    private ScheduleCreateTransitionLogic subject;

    @BeforeEach
    void setup() throws InvalidProtocolBufferException {
        validator = mock(OptionValidator.class);
        store = mock(ScheduleStore.class);
        accessor = mock(SignedTxnAccessor.class);
        activationHelper = mock(InHandleActivationHelper.class);
        replSigningWitness = mock(SignatoryUtils.ScheduledSigningsWitness.class);
        executor = mock(ScheduleExecutor.class);
        scheduleValue = mock(ScheduleVirtualValue.class);
        sigImpactHistorian = mock(SigImpactHistorian.class);
        properties = mock(GlobalDynamicProperties.class);
        scheduleProcessing = mock(ScheduleProcessing.class);
        usageLimits = mock(UsageLimits.class);
        given(accessor.getTxnBytes()).willReturn(bodyBytes);

        classifier = mock(SigMapScheduleClassifier.class);

        given(
                        replSigningWitness.observeInScope(
                                schedule, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(OK, true));

        given(executor.processImmediateExecution(any(), any(), any())).willReturn(OK);

        txnCtx = mock(TransactionContext.class);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.activePayerKey()).willReturn(payerKey);

        subject =
                new ScheduleCreateTransitionLogic(
                        usageLimits,
                        properties,
                        store,
                        txnCtx,
                        activationHelper,
                        validator,
                        executor,
                        sigImpactHistorian,
                        scheduleProcessing);

        subject.signingsWitness = replSigningWitness;
        subject.classifier = classifier;
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(scheduleCreateTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void followsHappyPath() throws InvalidProtocolBufferException {
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);
        given(scheduleValue.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(scheduleValue.calculatedExpirationTime()).willReturn(RichInstant.fromJava(now));
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(OK);
        givenValidTxnCtx();
        given(scheduleValue.adminKey()).willReturn(jAdminKey);

        subject.doStateTransition();

        verify(store).lookupSchedule(bodyBytes);
        verify(store).createProvisionally(scheduleValue, RichInstant.fromJava(now));
        verify(replSigningWitness)
                .observeInScope(schedule, store, validScheduleKeys, activationHelper, false);
        verify(store).commitCreation();
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(SUCCESS);
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(sigImpactHistorian).markEntityChanged(schedule.getScheduleNum());
        verify(executor).processImmediateExecution(schedule, store, txnCtx);
        verify(usageLimits).refreshSchedules();
    }

    @Test
    void followsHappyPathEvenIfNoNewValidSignatures() throws InvalidProtocolBufferException {
        given(scheduleValue.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(scheduleValue.calculatedExpirationTime()).willReturn(RichInstant.fromJava(now));
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(OK);
        givenValidTxnCtx();
        given(scheduleValue.adminKey()).willReturn(jAdminKey);
        given(
                        replSigningWitness.observeInScope(
                                schedule, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(NO_NEW_VALID_SIGNATURES, false));
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);

        subject.doStateTransition();

        verify(store).commitCreation();
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(SUCCESS);
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(executor, never()).processImmediateExecution(schedule, store, txnCtx);
    }

    @Test
    void doesNotExecuteWhenLongTermEnabledWaitForExpiry() throws InvalidProtocolBufferException {
        given(scheduleValue.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(scheduleValue.calculatedExpirationTime()).willReturn(RichInstant.fromJava(now));
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(OK);
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);
        givenValidTxnCtx();
        given(scheduleValue.adminKey()).willReturn(jAdminKey);
        given(scheduleValue.calculatedWaitForExpiry()).willReturn(true);
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(
                        replSigningWitness.observeInScope(
                                schedule, store, validScheduleKeys, activationHelper, true))
                .willReturn(Pair.of(OK, false));

        subject.doStateTransition();

        verify(store).lookupSchedule(bodyBytes);
        verify(store).createProvisionally(scheduleValue, RichInstant.fromJava(now));
        verify(replSigningWitness)
                .observeInScope(schedule, store, validScheduleKeys, activationHelper, true);
        verify(replSigningWitness, never()).observeInScope(any(), any(), any(), any(), eq(false));
        verify(store).commitCreation();
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(SUCCESS);
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(sigImpactHistorian).markEntityChanged(schedule.getScheduleNum());
        verify(executor, never()).processImmediateExecution(schedule, store, txnCtx);
    }

    @Test
    void followsHappyPathIfLongTermEnabledNotWaitForExpiry() throws InvalidProtocolBufferException {
        given(properties.schedulingLongTermEnabled()).willReturn(true);
        given(scheduleValue.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(scheduleValue.calculatedExpirationTime()).willReturn(RichInstant.fromJava(now));
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(OK);
        givenValidTxnCtx();
        given(scheduleValue.adminKey()).willReturn(jAdminKey);
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);

        subject.doStateTransition();

        verify(store).lookupSchedule(bodyBytes);
        verify(store).createProvisionally(scheduleValue, RichInstant.fromJava(now));
        verify(replSigningWitness)
                .observeInScope(schedule, store, validScheduleKeys, activationHelper, false);
        verify(replSigningWitness, never()).observeInScope(any(), any(), any(), any(), eq(true));
        verify(store).commitCreation();
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(SUCCESS);
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
        verify(sigImpactHistorian).markEntityChanged(schedule.getScheduleNum());
        verify(executor).processImmediateExecution(schedule, store, txnCtx);
    }

    @Test
    void checksUsageLimits() {
        givenValidTxnCtx();

        subject.doStateTransition();

        verify(store).lookupSchedule(bodyBytes);
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
    }

    @Test
    void rejectsRecreationOfExistingSchedule() {
        givenValidTxnCtx();
        given(scheduleValue.scheduledTransactionId()).willReturn(scheduledTxnId);
        given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(schedule, scheduleValue));

        subject.doStateTransition();

        verify(store, never()).createProvisionally(any(), any());
        verify(store, never()).commitCreation();
        verify(txnCtx, never()).addExpiringEntities(any());
        verify(txnCtx).setStatus(IDENTICAL_SCHEDULE_ALREADY_CREATED);
        verify(txnCtx).setCreated(schedule);
        verify(txnCtx).setScheduledTxnId(scheduledTxnId);
    }

    @Test
    void rollsBackForAnyNonOkSigning() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(scheduleValue.adminKey()).willReturn(jAdminKey);
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(OK);
        given(
                        replSigningWitness.observeInScope(
                                schedule, store, validScheduleKeys, activationHelper, false))
                .willReturn(Pair.of(SOME_SIGNATURES_WERE_INVALID, true));
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);

        subject.doStateTransition();

        verify(store).createProvisionally(scheduleValue, RichInstant.fromJava(now));
        verify(store, never()).commitCreation();
        verify(txnCtx).setStatus(SOME_SIGNATURES_WERE_INVALID);
        verify(executor, never()).processImmediateExecution(schedule, store, txnCtx);
    }

    @Test
    void capturesFailingCreateProvisionally() {
        givenValidTxnCtx();
        given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(null, scheduleValue));
        given(store.createProvisionally(scheduleValue, RichInstant.fromJava(now)))
                .willReturn(CreationResult.failure(INVALID_ADMIN_KEY));

        subject.doStateTransition();

        verify(store, never()).commitCreation();
        verify(txnCtx, never()).setStatus(SUCCESS);
    }

    @Test
    void capturesFailingThrottleCheck() throws InvalidProtocolBufferException {
        givenValidTxnCtx();
        given(scheduleProcessing.checkFutureThrottlesForCreate(schedule, scheduleValue))
                .willReturn(SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED);
        given(usageLimits.areCreatableSchedules(1)).willReturn(true);

        subject.doStateTransition();

        verify(store).createProvisionally(scheduleValue, RichInstant.fromJava(now));
        verify(store, never()).commitCreation();
        verify(txnCtx).setStatus(SCHEDULE_FUTURE_GAS_LIMIT_EXCEEDED);
        verify(executor, never()).processImmediateExecution(schedule, store, txnCtx);
    }

    @Test
    void setsFailInvalidIfUnhandledException() {
        givenValidTxnCtx();
        given(store.lookupSchedule(bodyBytes)).willThrow(IllegalArgumentException.class);

        subject.doStateTransition();

        verify(txnCtx).setStatus(FAIL_INVALID);
    }

    @Test
    void failsOnAdminKeySetAsSentinelKeylist() {
        invalidAdminKeyIsSentinelKeyList = true;
        givenCtx(true, false, false);

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    @Test
    void syntaxOkWithNoAdminKey() {
        adminKeyActuallySkipped = true;
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    @Test
    void failsOnInvalidAdminKey() {
        givenCtx(true, false, false);

        assertEquals(INVALID_ADMIN_KEY, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    @Test
    void failsOnInvalidEntityMemo() {
        givenCtx(false, true, false);

        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    @Test
    void failsOnInvalidInnerMemo() {
        givenCtx(false, false, true);

        assertEquals(INVALID_ZERO_BYTE_IN_STRING, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        assertEquals(OK, subject.semanticCheck().apply(scheduleCreateTxn));
    }

    private void givenValidTxnCtx() {
        givenCtx(false, false, false);
    }

    private void givenCtx(
            final boolean invalidAdminKey,
            final boolean invalidEntityMemo,
            final boolean invalidInnerMemo) {
        given(accessor.getSigMap()).willReturn(sigMap);
        given(
                        classifier.validScheduleKeys(
                                eq(List.of(payerKey, jAdminKey.get())), eq(sigMap), any(), any()))
                .willReturn(validScheduleKeys);

        final var builder = TransactionBody.newBuilder();
        final var scheduleCreate =
                ScheduleCreateTransactionBody.newBuilder()
                        .setAdminKey(key)
                        .setPayerAccountID(payer)
                        .setMemo(entityMemo)
                        .setScheduledTransactionBody(
                                SchedulableTransactionBody.newBuilder().setMemo(innerMemo));

        if (invalidAdminKey) {
            if (invalidAdminKeyIsSentinelKeyList) {
                scheduleCreate.setAdminKey(ImmutableKeyUtils.IMMUTABILITY_SENTINEL_KEY);
            } else {
                scheduleCreate.setAdminKey(invalidKey);
            }
        }

        if (adminKeyActuallySkipped) {
            scheduleCreate.clearAdminKey();
        }

        builder.setTransactionID(txnId);
        builder.setScheduleCreate(scheduleCreate);

        scheduleCreateTxn = builder.build();

        given(validator.memoCheck(entityMemo))
                .willReturn(invalidEntityMemo ? INVALID_ZERO_BYTE_IN_STRING : OK);
        given(validator.memoCheck(innerMemo))
                .willReturn(invalidInnerMemo ? INVALID_ZERO_BYTE_IN_STRING : OK);
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(scheduleCreateTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(payer);
        given(txnCtx.consensusTime()).willReturn(now);
        given(store.isCreationPending()).willReturn(true);
        given(store.lookupSchedule(bodyBytes)).willReturn(Pair.of(null, scheduleValue));
        given(store.createProvisionally(scheduleValue, RichInstant.fromJava(now)))
                .willReturn(CreationResult.success(schedule));
    }
}
