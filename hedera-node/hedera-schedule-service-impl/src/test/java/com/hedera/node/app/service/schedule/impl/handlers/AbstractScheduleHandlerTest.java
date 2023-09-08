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

package com.hedera.node.app.service.schedule.impl.handlers;

import static org.assertj.core.api.BDDAssertions.assertThat;
import static org.assertj.core.api.BDDAssertions.assertThatNoException;
import static org.assertj.core.api.BDDAssertions.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.impl.handlers.AbstractScheduleHandler.ScheduleKeysResult;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.VerificationAssistant;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Set;
import org.assertj.core.api.BDDAssertions;
import org.assertj.core.api.Condition;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

class AbstractScheduleHandlerTest extends ScheduleHandlerTestBase {
    private static final SchedulableTransactionBody NULL_SCHEDULABLE_BODY = null;
    private AbstractScheduleHandler testHandler;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        setUpBase();
        testHandler = new testScheduleHandler();
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
        BDDMockito.given(schedulesById.get(testScheduleID)).willReturn(null);
        assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testScheduleID))
                .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_SCHEDULE_ID));
        for (final Schedule next : listOfScheduledOptions) {
            final ScheduleID testId = next.scheduleId();
            // valid schedules should not throw
            BDDMockito.given(schedulesById.get(testId)).willReturn(next);
            assertThatNoException().isThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId));
            // scheduled without scheduled transaction should throw invalid transaction
            final Schedule missingScheduled = next.copyBuilder()
                    .scheduledTransaction(NULL_SCHEDULABLE_BODY)
                    .build();
            BDDMockito.given(schedulesById.get(testId)).willReturn(missingScheduled);
            assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_TRANSACTION));
            // Non-success codes returned by validate should become exceptions
            Schedule otherFailures = next.copyBuilder().executed(true).build();
            BDDMockito.given(schedulesById.get(testId)).willReturn(otherFailures);
            assertThatThrownBy(() -> testHandler.preValidate(scheduleStore, false, testId))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.SCHEDULE_ALREADY_EXECUTED));
            otherFailures = next.copyBuilder().deleted(true).build();
            BDDMockito.given(schedulesById.get(testId)).willReturn(otherFailures);
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
    void verifyCheckTxnId() throws PreCheckException {
        assertThatThrownBy(new callCheckValid(null, testHandler))
                .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_TRANSACTION_ID));
        for (final Schedule next : listOfScheduledOptions) {
            final TransactionID idToTest = next.originalCreateTransaction().transactionID();
            assertThatNoException().isThrownBy(new callCheckValid(idToTest, testHandler));
            TransactionID brokenId = idToTest.copyBuilder().scheduled(true).build();
            assertThatThrownBy(new callCheckValid(brokenId, testHandler))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST));
            brokenId = idToTest.copyBuilder().accountID((AccountID) null).build();
            assertThatThrownBy(new callCheckValid(brokenId, testHandler))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID));
            brokenId = idToTest.copyBuilder()
                    .transactionValidStart((Timestamp) null)
                    .build();
            assertThatThrownBy(new callCheckValid(brokenId, testHandler))
                    .is(new PreCheckExceptionMatch(ResponseCodeEnum.INVALID_TRANSACTION_START));
        }
    }

    @Test
    void verifyKeysForPreHandle() throws PreCheckException {
        // Run through the "standard" schedules to ensure we handle the common cases
        for (final Schedule next : listOfScheduledOptions) {
            realPreContext = new PreHandleContextImpl(
                    mockStoreFactory, next.originalCreateTransaction(), testConfig, mockDispatcher);
            Set<Key> keysObtained = testHandler.allKeysForTransaction(next, realPreContext);
            assertThat(keysObtained).isNotEmpty().hasSize(1).containsExactly(schedulerKey);
        }
        // One check with a complex set of key returns, to ensure we process required and optional correctly.
        final TransactionKeys testKeys =
                new TestTransactionKeys(schedulerKey, Set.of(payerKey, adminKey), Set.of(optionKey, otherKey));
        // Must spy the context for this, the real dispatch would require calling other service handlers
        PreHandleContext spyableContext = new PreHandleContextImpl(
                mockStoreFactory, scheduleInState.originalCreateTransaction(), testConfig, mockDispatcher);
        PreHandleContext spiedContext = BDDMockito.spy(spyableContext);
        // given...return fails because it calls the real method before it can be replaced.
        BDDMockito.doReturn(testKeys).when(spiedContext).allKeysForTransaction(BDDMockito.any(), BDDMockito.any());
        final Set<Key> keysObtained = testHandler.allKeysForTransaction(scheduleInState, spiedContext);
        assertThat(keysObtained).isNotEmpty();
        assertThat(keysObtained).containsExactlyInAnyOrder(payerKey, schedulerKey, adminKey, optionKey, otherKey);
    }

    @Test
    void varifyKeysForHandle() throws PreCheckException {
        final TransactionKeys testKeys =
                new TestTransactionKeys(schedulerKey, Set.of(payerKey, adminKey), Set.of(optionKey, schedulerKey));
        BDDMockito.given(mockContext.allKeysForTransaction(BDDMockito.any(), BDDMockito.any()))
                .willReturn(testKeys);
        final AccountID payerAccountId = schedulerAccount.accountId();
        BDDMockito.given(mockContext.payer()).willReturn(payerAccountId);
        // This is how you get side-effects replicated, by having the "Answer" called in place of the real method.
        BDDMockito.given((mockContext).verificationFor(BDDMockito.any(), BDDMockito.any()))
                .will(new VerificationForAnswer(testKeys));
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
            assertThat(keysRequired).isNotEmpty().hasSize(2).containsExactlyInAnyOrder(optionKey, schedulerKey);
            assertThat(keysObtained).isNotEmpty().hasSize(2).containsExactlyInAnyOrder(payerKey, adminKey);
        }
    }

    @Test
    void verifyTryExecute() {
        final var mockRecordBuilder = Mockito.mock(SingleTransactionRecordBuilderImpl.class);
        BDDMockito.given(mockContext.dispatchChildTransaction(
                        Mockito.any(TransactionBody.class),
                        Mockito.any(),
                        Mockito.any(ScheduleVerificationAssistant.class)))
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
            assertThatThrownBy(() -> testHandler.tryToExecuteSchedule(
                            mockContext, testItem, testRemaining, testSignatories, ResponseCodeEnum.OK, false))
                    .is(new HandleExceptionMatch(ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE));
        }
    }

    // This provides Mock answers for Context code.  In order to actually test the Handler code, however, this
    // class MUST call the callback for each key, and generate an success/failure based on whether the key is in
    // the required (success) or optional (failure) set in the TransactionKeys provided to the constructor.
    // Not calling the callback, passing a different key, or not responding with a correct Verification could
    // cause incorrect test results and permit errors to pass testing.
    private static final class VerificationForAnswer implements Answer<SignatureVerification> {
        private static final String TYPE_FAIL_MESSAGE = "Incorrect Argument type, expected %s but got %s";
        private final TransactionKeys keysForTransaction;

        VerificationForAnswer(TransactionKeys testKeys) {
            keysForTransaction = testKeys;
        }

        @Override
        public SignatureVerification answer(final InvocationOnMock invocation) {
            final SignatureVerification result;
            final Object[] arguments = invocation.getArguments();
            if (arguments.length != 2) {
                result = null;
                BDDAssertions.fail("Incorrect Argument count, expected 2 but got %d".formatted(arguments.length));
            } else if (arguments[0] instanceof Key keyToTest) {
                if (arguments[1] instanceof VerificationAssistant callback) {
                    if (keysForTransaction.requiredNonPayerKeys().contains(keyToTest)) {
                        result = new SignatureVerificationImpl(keyToTest, null, true);
                        callback.test(keyToTest, result);
                    } else {
                        result = new SignatureVerificationImpl(keyToTest, null, false);
                        callback.test(keyToTest, new SignatureVerificationImpl(keyToTest, null, false));
                    }
                } else {
                    result = null;
                    final String actualType = arguments[1].getClass().getCanonicalName();
                    BDDAssertions.fail(TYPE_FAIL_MESSAGE.formatted("VerificationAssistant", actualType));
                }
            } else {
                result = null;
                final String actualType = arguments[1].getClass().getCanonicalName();
                BDDAssertions.fail(TYPE_FAIL_MESSAGE.formatted("Key", actualType));
            }
            return result;
        }
    }

    // Callable required by AssertJ throw assertions; unavoidable due to limitations on lambda syntax.
    private static final class callCheckValid implements ThrowingCallable {
        private final TransactionID idToTest;
        private final AbstractScheduleHandler testHandler;

        callCheckValid(final TransactionID idToTest, final AbstractScheduleHandler testHandler) {
            this.idToTest = idToTest;
            this.testHandler = testHandler;
        }

        @Override
        public void call() throws PreCheckException {
            testHandler.checkValidTransactionId(idToTest);
        }
    }

    private static final class testScheduleHandler extends AbstractScheduleHandler {}

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

    private static class TestTransactionKeys implements TransactionKeys {
        private final Key payerKey;
        private final Set<Key> optionalKeys;
        private final Set<Key> requiredKeys;

        private TestTransactionKeys(final Key payer, final Set<Key> required, final Set<Key> optional) {
            payerKey = payer;
            requiredKeys = required;
            optionalKeys = optional;
        }

        @Override
        public Key payerKey() {
            return payerKey;
        }

        @Override
        public Set<Key> requiredNonPayerKeys() {
            return requiredKeys;
        }

        @Override
        public Set<Account> requiredHollowAccounts() {
            return null;
        }

        @Override
        public Set<Key> optionalNonPayerKeys() {
            return optionalKeys;
        }

        @Override
        public Set<Account> optionalHollowAccounts() {
            return null;
        }
    }
}
