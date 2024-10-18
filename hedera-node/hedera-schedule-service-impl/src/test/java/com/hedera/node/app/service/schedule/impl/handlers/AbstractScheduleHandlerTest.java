/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.handlers;

import static org.assertj.core.api.BDDAssertions.assertThat;
import static org.assertj.core.api.BDDAssertions.assertThatNoException;
import static org.assertj.core.api.BDDAssertions.assertThatThrownBy;
import static org.mockito.Mockito.any;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.handlers.AbstractScheduleHandler.ScheduleKeysResult;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

class AbstractScheduleHandlerTest extends ScheduleHandlerTestBase {
    private static final SchedulableTransactionBody NULL_SCHEDULABLE_BODY = null;
    private AbstractScheduleHandler testHandler;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
        testHandler = new TestScheduleHandler();
    }

    @Test
    void validationOkReturnsSuccessForOKAndPending() {
        for (final ResponseCodeEnum testValue : ResponseCodeEnum.values()) {
            switch (testValue) {
                case SUCCESS, OK, SCHEDULE_PENDING_EXPIRATION -> assertThat(testHandler.validationOk(testValue))
                        .isTrue();
                default -> assertThat(testHandler.validationOk(testValue)).isFalse();
            }
        }
    }

    @Test
    void preValidateVerifiesSchedulableAndID() throws PreCheckException {
        // missing schedule or schedule ID should throw invalid ID
        assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, null))
                .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_SCHEDULE_ID));
        reset(writableById);
        scheduleMapById.put(testScheduleID, null);
        assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testScheduleID))
                .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_SCHEDULE_ID));
        for (final Schedule next : listOfScheduledOptions) {
            final ScheduleID testId = next.scheduleId();
            reset(writableById);
            // valid schedules should not throw
            scheduleMapById.put(testId, next);
            assertThatNoException().isThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId));
            // scheduled without scheduled transaction should throw invalid transaction
            final Schedule missingScheduled = next.copyBuilder()
                    .scheduledTransaction(NULL_SCHEDULABLE_BODY)
                    .build();
            reset(writableById);
            scheduleMapById.put(testId, missingScheduled);
            assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_TRANSACTION));
            // Non-success codes returned by validate should become exceptions
            reset(writableById);
            scheduleMapById.put(testId, next.copyBuilder().executed(true).build());
            assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED));

            reset(writableById);
            scheduleMapById.put(testId, next.copyBuilder().deleted(true).build());
            assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.SCHEDULE_ALREADY_DELETED));
        }
    }

    @Test
    void validateVerifiesExecutionDeletionAndExpiration() {
        assertThat(testHandler.validate(null, testConsensusTime, false))
                .isEqualTo(ResponseCodeEnum.INVALID_SCHEDULE_ID);
        for (final Schedule next : listOfScheduledOptions) {
            assertThat(testHandler.validate(next, testConsensusTime, false)).isEqualTo(ResponseCodeEnum.OK);
            assertThat(testHandler.validate(next, testConsensusTime, true)).isEqualTo(ResponseCodeEnum.OK);
            Schedule failures = next.copyBuilder()
                    .scheduledTransaction(NULL_SCHEDULABLE_BODY)
                    .build();
            assertThat(testHandler.validate(failures, testConsensusTime, false))
                    .isEqualTo(ResponseCodeEnum.INVALID_TRANSACTION);
            failures = next.copyBuilder().executed(true).build();
            assertThat(testHandler.validate(failures, testConsensusTime, false))
                    .isEqualTo(ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED);
            failures = next.copyBuilder().deleted(true).build();
            assertThat(testHandler.validate(failures, testConsensusTime, false))
                    .isEqualTo(ResponseCodeEnum.SCHEDULE_ALREADY_DELETED);
            final Instant consensusAfterExpiration = Instant.ofEpochSecond(next.calculatedExpirationSecond() + 5);
            assertThat(testHandler.validate(next, consensusAfterExpiration, false))
                    .isEqualTo(ResponseCodeEnum.INVALID_SCHEDULE_ID);
            assertThat(testHandler.validate(next, consensusAfterExpiration, true))
                    .isEqualTo(ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION);
        }
    }

    @Test
    void verifyKeysForPreHandle() throws PreCheckException {
        // Run through the "standard" schedules to ensure we handle the common cases
        for (final Schedule next : listOfScheduledOptions) {
            realPreContext = new PreHandleContextImpl(
                    mockStoreFactory, next.originalCreateTransaction(), testConfig, mockDispatcher);
            Set<Key> keysObtained = testHandler.allKeysForTransaction(next, realPreContext);
            // Should have no keys, because the mock dispatcher returns no keys
            assertThat(keysObtained).isEmpty();
        }
        // One check with a complex set of key returns, to ensure we process required and optional correctly.
        final TransactionKeys testKeys =
                new TestTransactionKeys(schedulerKey, Set.of(payerKey, adminKey), Set.of(optionKey, otherKey));
        // Must spy the context for this, the real dispatch would require calling other service handlers
        PreHandleContext spyableContext = new PreHandleContextImpl(
                mockStoreFactory, scheduleInState.originalCreateTransaction(), testConfig, mockDispatcher);
        PreHandleContext spiedContext = BDDMockito.spy(spyableContext);
        // given...return fails because it calls the real method before it can be replaced.
        BDDMockito.doReturn(testKeys).when(spiedContext).allKeysForTransaction(any(), any());
        final Set<Key> keysObtained = testHandler.allKeysForTransaction(scheduleInState, spiedContext);
        assertThat(keysObtained).isNotEmpty();
        assertThat(keysObtained).containsExactly(adminKey, optionKey, otherKey, payerKey);
    }

    @Test
    void verifyKeysForHandle() throws PreCheckException {
        final TransactionKeys testKeys =
                new TestTransactionKeys(schedulerKey, Set.of(payerKey, adminKey), Set.of(optionKey, schedulerKey));
        BDDMockito.given(mockContext.allKeysForTransaction(any(), any())).willReturn(testKeys);
        final AccountID payerAccountId = schedulerAccount.accountId();
        BDDMockito.given(mockContext.payer()).willReturn(payerAccountId);
        // This is how you get side-effects replicated, by having the "Answer" called in place of the real method.
        BDDMockito.given(keyVerifier.verificationFor(any(), any())).will(new VerificationForAnswer(testKeys));
        // For this test, Context must mock `payer()`, `allKeysForTransaction()`, and `verificationFor`
        // `verificationFor` is needed because we check verification in allKeysForTransaction to reduce
        // the required keys set (potentially to empty) during handle.  We must use an "Answer" for verification
        // because verificationFor relies on side-effects for important results.
        // Run through the "standard" schedules to ensure we handle the common cases
        for (final Schedule next : listOfScheduledOptions) {
            final ScheduleKeysResult verificationResult = testHandler.allKeysForTransaction(next, mockContext);
            final Set<Key> keysRequired = verificationResult.remainingRequiredKeys();
            final Set<Key> keysObtained = verificationResult.updatedSignatories();
            // we *mock* verificationFor side effects, which is what fills in/clears the sets,
            // so results should all be the same, despite empty signatories and mocked HandleContext.
            // We do so based on verifier calls, so it still exercises the code to be tested, however.
            // @todo('9447') add the schedulerKey back in.
            // Note, however, we exclude the schedulerKey because it paid for the original create, so it
            //    is "deemed valid" and not included.
            assertThat(keysRequired).isNotEmpty().hasSize(1).containsExactly(optionKey);
            assertThat(keysObtained).isNotEmpty().hasSize(2).containsExactly(adminKey, payerKey);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void verifyTryExecute() {
        final var mockRecordBuilder = Mockito.mock(RecordStreamBuilder.class);
        BDDMockito.given(mockContext.dispatchChildTransaction(
                        any(TransactionBody.class),
                        any(),
                        any(Predicate.class),
                        any(AccountID.class),
                        any(TransactionCategory.class),
                        any()))
                .willReturn(mockRecordBuilder);
        for (final Schedule testItem : listOfScheduledOptions) {
            Set<Key> testRemaining = Set.of();
            final Set<Key> testSignatories = Set.of(adminKey, payerKey);
            BDDMockito.given(mockRecordBuilder.status()).willReturn(ResponseCodeEnum.OK);
            ResponseCodeEnum priorResponse = ResponseCodeEnum.SUCCESS;
            assertThat(testHandler.tryToExecuteSchedule(
                            mockContext, testItem, testRemaining, testSignatories, priorResponse, false))
                    .isTrue();
            priorResponse = ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
            assertThat(testHandler.tryToExecuteSchedule(
                            mockContext, testItem, testRemaining, testSignatories, priorResponse, false))
                    .isTrue();
            priorResponse = ResponseCodeEnum.SCHEDULE_PENDING_EXPIRATION;
            assertThat(testHandler.tryToExecuteSchedule(
                            mockContext, testItem, testRemaining, testSignatories, priorResponse, true))
                    .isFalse();
            BDDMockito.given(mockRecordBuilder.status()).willReturn(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE);
            assertThatNoException()
                    .isThrownBy(() -> testHandler.tryToExecuteSchedule(
                            mockContext, testItem, testRemaining, testSignatories, ResponseCodeEnum.OK, false));
        }
    }

    private static final class TestScheduleHandler extends AbstractScheduleHandler {}

    private static final class PreCheckExceptionMatch extends Condition<Throwable> {
        private final ResponseCodeEnum codeToMatch;

        PreCheckExceptionMatch(final ResponseCodeEnum codeToMatch) {
            this.codeToMatch = codeToMatch;
        }

        @Override
        public boolean matches(final Throwable thrown) {
            return thrown instanceof PreCheckException e && e.responseCode() == codeToMatch;
        }
    }

    private static final class HandleExceptionMatch extends Condition<Throwable> {
        private final ResponseCodeEnum codeToMatch;

        HandleExceptionMatch(final ResponseCodeEnum codeToMatch) {
            this.codeToMatch = codeToMatch;
        }

        @Override
        public boolean matches(final Throwable thrown) {
            return thrown instanceof HandleException e && e.getStatus() == codeToMatch;
        }
    }
}
