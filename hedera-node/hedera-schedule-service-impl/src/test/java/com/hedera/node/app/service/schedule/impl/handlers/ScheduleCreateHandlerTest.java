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

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static org.assertj.core.api.BDDAssertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody.DataOneOfType;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ScheduledTransactionFactory;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.fixtures.Assertions;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.VerificationAssistant;
import com.hedera.node.app.workflows.prehandle.PreHandleContextImpl;
import java.security.InvalidKeyException;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;
import org.mockito.Mockito;

class ScheduleCreateHandlerTest extends ScheduleHandlerTestBase {
    private ScheduleCreateHandler subject;
    private PreHandleContext realPreContext;

    @BeforeEach
    void setUp() throws PreCheckException, InvalidKeyException {
        subject = new ScheduleCreateHandler();
        setUpBase();
    }

    @Test
    void preHandleVanilla() throws PreCheckException {
        realPreContext = new PreHandleContextImpl(
                mockStoreFactory, scheduleCreateTransaction(payer), testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().hasSize(1);
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().hasSize(1);

        assertThat(realPreContext.requiredNonPayerKeys()).isEqualTo(Set.of(adminKey));
        assertThat(realPreContext.optionalNonPayerKeys()).isEqualTo(Set.of(payerKey));

        assertThat(mockContext).isNotNull();
    }

    @Test
    void preHandleVanillaNoAdmin() throws PreCheckException {
        final TransactionBody transactionToTest = ScheduledTransactionFactory.scheduleCreateTransactionWith(
                null, "", payer, scheduler, Timestamp.newBuilder().seconds(1L).build());
        realPreContext = new PreHandleContextImpl(mockStoreFactory, transactionToTest, testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().isEmpty();
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().hasSize(1);

        assertThat(realPreContext.optionalNonPayerKeys()).isEqualTo(Set.of(payerKey));
    }

    @Test
    void preHandleUsesCreatePayerIfScheduledPayerNotSet() throws PreCheckException {
        realPreContext =
                new PreHandleContextImpl(mockStoreFactory, scheduleCreateTransaction(null), testConfig, mockDispatcher);
        subject.preHandle(realPreContext);

        assertThat(realPreContext).isNotNull();
        assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
        assertThat(realPreContext.requiredNonPayerKeys()).isNotNull().hasSize(1);
        assertThat(realPreContext.optionalNonPayerKeys()).isNotNull().isEmpty();

        assertThat(realPreContext.requiredNonPayerKeys()).isEqualTo(Set.of(adminKey));
    }

    @Test
    void preHandleMissingPayerThrowsInvalidPayer() throws PreCheckException {
        reset(accountById);
        accountsMapById.put(payer, null);

        final TransactionBody createBody = scheduleCreateTransaction(payer);
        realPreContext = new PreHandleContextImpl(mockStoreFactory, createBody, testConfig, mockDispatcher);
        Assertions.assertThrowsPreCheck(
                () -> subject.preHandle(realPreContext), ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
    }

    @Test
    void preHandleRejectsNonWhitelist() throws PreCheckException {
        final Set<HederaFunctionality> configuredWhitelist =
                scheduleConfig.whitelist().functionalitySet();
        for (final Schedule next : listOfScheduledOptions) {
            final TransactionBody createTransaction = next.originalCreateTransaction();
            final SchedulableTransactionBody child = next.scheduledTransaction();
            final DataOneOfType transactionType = child.data().kind();
            final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
            realPreContext = new PreHandleContextImpl(mockStoreFactory, createTransaction, testConfig, mockDispatcher);
            if (configuredWhitelist.contains(functionType)) {
                subject.preHandle(realPreContext);
                assertThat(realPreContext.payerKey()).isNotNull().isEqualTo(schedulerKey);
            } else {
                Assertions.assertThrowsPreCheck(
                        () -> subject.preHandle(realPreContext),
                        ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
            }
        }
    }

    @Test
    void handleRejectsDuplicateTransaction() throws PreCheckException {
        final TransactionBody createTransaction = otherScheduleInState.originalCreateTransaction();
        prepareContext(createTransaction, otherScheduleInState.scheduleId().scheduleNum() + 1);
        throwsHandleException(() -> subject.handle(mockContext), ResponseCodeEnum.IDENTICAL_SCHEDULE_ALREADY_CREATED);
    }

    @Test
    void verifyPureChecks() throws PreCheckException {
        final TransactionBody.Builder failures = alternateCreateTransaction.copyBuilder();
        final TransactionID originalId = alternateCreateTransaction.transactionID();
        Assertions.assertThrowsPreCheck(() -> subject.pureChecks(null), ResponseCodeEnum.INVALID_TRANSACTION_BODY);
        failures.transactionID(nullTransactionId);
        Assertions.assertThrowsPreCheck(
                () -> subject.pureChecks(failures.build()), ResponseCodeEnum.INVALID_TRANSACTION_ID);
        TransactionID.Builder idErrors = originalId.copyBuilder().scheduled(true);
        failures.transactionID(idErrors);
        Assertions.assertThrowsPreCheck(
                () -> subject.pureChecks(failures.build()), ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
        idErrors = originalId.copyBuilder().transactionValidStart(nullTime);
        failures.transactionID(idErrors);
        Assertions.assertThrowsPreCheck(
                () -> subject.pureChecks(failures.build()), ResponseCodeEnum.INVALID_TRANSACTION_START);
        idErrors = originalId.copyBuilder().accountID(nullAccount);
        failures.transactionID(idErrors);
        Assertions.assertThrowsPreCheck(
                () -> subject.pureChecks(failures.build()), ResponseCodeEnum.INVALID_SCHEDULE_PAYER_ID);
        failures.transactionID(originalId);
        setLongTermError(failures, alternateCreateTransaction);
        // The code here should be INVALID_LONG_TERM_SCHEDULE when/if that is added to response codes.
        Assertions.assertThrowsPreCheck(
                () -> subject.pureChecks(failures.build()), ResponseCodeEnum.INVALID_TRANSACTION);
    }

    private void setLongTermError(final TransactionBody.Builder failures, final TransactionBody original) {
        final var createBuilder = original.scheduleCreate().copyBuilder();
        createBuilder.waitForExpiry(true).expirationTime(nullTime);
        failures.scheduleCreate(createBuilder);
    }

    @Test
    void handleRejectsNonWhitelist() throws HandleException, PreCheckException {
        final Set<HederaFunctionality> configuredWhitelist =
                scheduleConfig.whitelist().functionalitySet();
        for (final Schedule next : listOfScheduledOptions) {
            final TransactionBody createTransaction = next.originalCreateTransaction();
            final TransactionID createId = createTransaction.transactionID();
            final SchedulableTransactionBody child = next.scheduledTransaction();
            final DataOneOfType transactionType = child.data().kind();
            final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
            prepareContext(createTransaction, next.scheduleId().scheduleNum());
            final int startCount = scheduleMapById.size();
            if (configuredWhitelist.contains(functionType)) {
                subject.handle(mockContext);
                verifyHandleSucceededForWhitelist(next, createId, startCount);
            } else {
                throwsHandleException(
                        () -> subject.handle(mockContext), ResponseCodeEnum.SCHEDULED_TRANSACTION_NOT_IN_WHITELIST);
            }
        }
    }

    @Test
    void handleRefusesToExceedCreationLimit() throws HandleException, PreCheckException {
        final Set<HederaFunctionality> configuredWhitelist =
                scheduleConfig.whitelist().functionalitySet();
        assertThat(configuredWhitelist).hasSizeGreaterThan(4);

        final WritableScheduleStore fullStore = mock(WritableScheduleStore.class);
        given(fullStore.numSchedulesInState()).willReturn(scheduleConfig.maxNumber() + 1);
        given(mockContext.writableStore(WritableScheduleStore.class)).willReturn(fullStore);

        for (final Schedule next : listOfScheduledOptions) {
            final TransactionBody createTransaction = next.originalCreateTransaction();
            final SchedulableTransactionBody child = next.scheduledTransaction();
            final DataOneOfType transactionType = child.data().kind();
            final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
            prepareContext(createTransaction, next.scheduleId().scheduleNum());
            // all keys are "valid" with this mock setup
            given(mockContext.verificationFor(BDDMockito.any(Key.class), BDDMockito.any(VerificationAssistant.class)))
                    .willReturn(new SignatureVerificationImpl(nullKey, null, true));
            if (configuredWhitelist.contains(functionType)) {
                throwsHandleException(
                        () -> subject.handle(mockContext), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
            }
        }
    }

    @Test
    void handleExecutesImmediateIfPossible() throws HandleException, PreCheckException {
        final Set<HederaFunctionality> configuredWhitelist =
                scheduleConfig.whitelist().functionalitySet();
        int successCount = 0;
        // make sure we have at least four items in the whitelist to test.
        assertThat(configuredWhitelist.size()).isGreaterThan(4);
        for (final Schedule next : listOfScheduledOptions) {
            final TransactionBody createTransaction = next.originalCreateTransaction();
            final TransactionID createId = createTransaction.transactionID();
            final SchedulableTransactionBody child = next.scheduledTransaction();
            final DataOneOfType transactionType = child.data().kind();
            final HederaFunctionality functionType = HandlerUtility.functionalityForType(transactionType);
            prepareContext(createTransaction, next.scheduleId().scheduleNum());
            // all keys are "valid" with this mock setup
            given(mockContext.verificationFor(BDDMockito.any(Key.class), BDDMockito.any(VerificationAssistant.class)))
                    .willReturn(new SignatureVerificationImpl(nullKey, null, true));
            final int startCount = scheduleMapById.size();
            if (configuredWhitelist.contains(functionType)) {
                subject.handle(mockContext);
                verifyHandleSucceededAndExecuted(next, createId, startCount);
                successCount++;
            } // only using whitelisted txns for this test
        }
        // verify that all the whitelisted txns actually executed and verified.
        assertThat(successCount).isEqualTo(configuredWhitelist.size());
    }

    private void verifyHandleSucceededForWhitelist(
            final Schedule next, final TransactionID createId, final int startCount) {
        commit(writableById); // commit changes so we can inspect the underlying map
        // should be a new schedule in the map
        assertThat(scheduleMapById.size()).isEqualTo(startCount + 1);
        // verifying that the handle really ran and created the new schedule
        final Schedule wrongSchedule = writableSchedules.get(next.scheduleId());
        assertThat(wrongSchedule).isNull(); // shard and realm *should not* match here
        // get a corrected schedule ID.
        final ScheduleID correctedId = adjustRealmShardForPayer(next, createId);
        final Schedule resultSchedule = writableSchedules.get(correctedId);
        // verify the schedule was created ready for sign transactions
        assertThat(resultSchedule).isNotNull(); // shard and realm *should* match here
        assertThat(resultSchedule.deleted()).isFalse();
        assertThat(resultSchedule.executed()).isFalse();
    }

    private TransactionBody scheduleCreateTransaction(final AccountID payer) {
        final Timestamp timestampValue =
                Timestamp.newBuilder().seconds(1_234_567L).build();
        return ScheduledTransactionFactory.scheduleCreateTransactionWith(
                adminKey, "test", payer, scheduler, timestampValue);
    }

    private void prepareContext(final TransactionBody createTransaction, final long nextEntityId)
            throws PreCheckException {
        given(mockContext.body()).willReturn(createTransaction);
        given(mockContext.newEntityNum()).willReturn(nextEntityId);
        given(mockContext.allKeysForTransaction(Mockito.any(), Mockito.any())).willReturn(testChildKeys);
        // This is how you get side-effects replicated, by having the "Answer" called in place of the real method.
        given(mockContext.verificationFor(BDDMockito.any(Key.class), BDDMockito.any(VerificationAssistant.class)))
                .will(new VerificationForAnswer(testChildKeys));
    }
}
