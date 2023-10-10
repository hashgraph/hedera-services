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

import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.failedVerification;
import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.passedVerification;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.schedule.WritableScheduleStore;
import com.hedera.node.app.service.schedule.impl.ScheduleTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.VerificationAssistant;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Set;
import java.util.function.Predicate;
import org.assertj.core.api.BDDAssertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

// TODO: Make this extend ScheduleTestBase in the enclosing package
@SuppressWarnings("ProtectedField")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.WARN)
class ScheduleHandlerTestBase extends ScheduleTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    protected TransactionDispatcher mockDispatcher;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected HandleContext mockContext;

    protected final TransactionKeys testChildKeys =
            createChildKeys(adminKey, schedulerKey, payerKey, optionKey, otherKey);

    protected void setUpBase() throws PreCheckException, InvalidKeyException {
        super.setUpBase();
        setUpContext();
    }

    protected void throwsHandleException(final ThrowingCallable callable, final ResponseCodeEnum expectedError) {
        assertThatThrownBy(callable)
                .isInstanceOf(HandleException.class)
                .hasFieldOrPropertyWithValue("status", expectedError);
    }

    protected void verifyHandleSucceededAndExecuted(
            final Schedule next, final TransactionID parentId, final int startCount) {
        commit(writableById); // commit changes so we can inspect the underlying map
        // should be a new schedule in the map
        assertThat(scheduleMapById.size()).isEqualTo(startCount + 1);
        // verifying that the handle really ran and created the new schedule
        final Schedule wrongSchedule = writableSchedules.get(next.scheduleId());
        assertThat(wrongSchedule).isNull(); // shard and realm *should not* match here
        // get a corrected schedule ID.
        final ScheduleID correctedId = adjustRealmShardForPayer(next, parentId);
        final Schedule resultSchedule = writableSchedules.get(correctedId);
        // verify the schedule was created ready for sign transactions
        assertThat(resultSchedule).isNotNull(); // shard and realm *should* match here
        assertThat(resultSchedule.deleted()).isFalse();
        // The scheduled should have executed immediately; for these tests we validate all required
        //     signatures in the test create transaction.
        assertThat(resultSchedule.executed()).isTrue();
        assertThat(resultSchedule.resolutionTime()).isEqualTo(timestampFrom(testConsensusTime));
    }

    protected void verifySignHandleSucceededAndExecuted(
            final Schedule next, final TransactionID parentId, final int startCount) {
        commit(writableById); // commit changes so we can inspect the underlying map
        // should be a new schedule in the map
        assertThat(scheduleMapById.size()).isEqualTo(startCount + 1);
        // verifying that the handle really ran and created the new schedule
        final Schedule signedSchedule = writableSchedules.get(next.scheduleId());
        assertThat(signedSchedule).isNotNull(); // shard and realm *should* match here
        assertThat(signedSchedule.deleted()).isFalse();
        // The scheduled should have executed immediately; for these tests we validate all required
        //     signatures in the test sign transaction.
        assertThat(signedSchedule.executed()).isTrue();
        assertThat(signedSchedule.resolutionTime()).isEqualTo(timestampFrom(testConsensusTime));
    }

    protected void verifySignHandleSucceededNoExecution(
            final Schedule next, final TransactionID parentId, final int startCount) {
        commit(writableById); // commit changes so we can inspect the underlying map
        // should be a new schedule in the map
        assertThat(scheduleMapById.size()).isEqualTo(startCount + 1);
        // verifying that the handle really ran and created the new schedule
        final Schedule signedSchedule = writableSchedules.get(next.scheduleId());
        assertThat(signedSchedule).isNotNull(); // shard and realm *should* match here
        assertThat(signedSchedule.deleted()).isFalse();
        // The scheduled should NOT have executed; for these tests we validate only some required
        //     signatures in the test sign transaction.
        assertThat(signedSchedule.executed()).isFalse();
        assertThat(signedSchedule.resolutionTime()).isNull();
    }

    protected static ScheduleID adjustRealmShardForPayer(final Schedule next, final TransactionID parentId) {
        long correctRealm = parentId.accountID().realmNum();
        long correctShard = parentId.accountID().shardNum();
        final ScheduleID.Builder correctedBuilder = next.scheduleId().copyBuilder();
        correctedBuilder.realmNum(correctRealm).shardNum(correctShard);
        final ScheduleID correctedId = correctedBuilder.build();
        return correctedId;
    }

    protected Timestamp timestampFrom(final Instant valueToConvert) {
        return new Timestamp(valueToConvert.getEpochSecond(), valueToConvert.getNano());
    }

    protected Timestamp timestampFrom(final long secondsToConvert) {
        return new Timestamp(secondsToConvert, 0);
    }

    @SuppressWarnings("unchecked")
    private void setUpContext() {
        given(mockContext.configuration()).willReturn(testConfig);
        given(mockContext.consensusNow()).willReturn(testConsensusTime);
        given(mockContext.attributeValidator()).willReturn(new AttributeValidatorImpl(mockContext));
        given(mockContext.payer()).willReturn(payer);
        given(mockContext.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(mockContext.readableStore(ReadableScheduleStore.class)).willReturn(scheduleStore);
        given(mockContext.writableStore(WritableScheduleStore.class)).willReturn(writableSchedules);
        given(mockContext.verificationFor(eq(payerKey), any())).willReturn(passedVerification(payerKey));
        given(mockContext.verificationFor(eq(adminKey), any())).willReturn(passedVerification(adminKey));
        given(mockContext.verificationFor(eq(schedulerKey), any())).willReturn(failedVerification(schedulerKey));
        given(mockContext.verificationFor(eq(optionKey), any())).willReturn(failedVerification(optionKey));
        given(mockContext.verificationFor(eq(otherKey), any())).willReturn(failedVerification(otherKey));
        given(mockContext.dispatchChildTransaction(any(), eq(ScheduleRecordBuilder.class), any(Predicate.class)))
                .willReturn(new SingleTransactionRecordBuilderImpl(testConsensusTime));
        given(mockContext.recordBuilder(ScheduleRecordBuilder.class))
                .willReturn(new SingleTransactionRecordBuilderImpl(testConsensusTime));
    }

    private static TransactionKeys createChildKeys(
            final Key payerKey, final Key adminKey, final Key schedulerKey, final Key... optionalKeys) {
        return new TestTransactionKeys(payerKey, Set.of(adminKey, schedulerKey), Set.of(optionalKeys));
    }

    // This provides Mock answers for Context code.  In order to actually test the Handler code, however, this
    // class MUST call the callback for each key, and generate an success/failure based on whether the key is in
    // the required (success) or optional (failure) set in the TransactionKeys provided to the constructor.
    // Not calling the callback, passing a different key, or not responding with a correct Verification could
    // cause incorrect test results and permit errors to pass testing.
    protected static final class VerificationForAnswer implements Answer<SignatureVerification> {
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
                    // Spotless forces this layout, because it mangles ternaries early
                    final String actualType;
                    if (arguments[1] == null) actualType = "null";
                    else actualType = arguments[1].getClass().getCanonicalName();
                    BDDAssertions.fail(TYPE_FAIL_MESSAGE.formatted("VerificationAssistant", actualType));
                }
            } else {
                result = null;
                // just barely short enough to avoid spotless mangling
                final String actualType =
                        arguments[0] == null ? "null" : arguments[0].getClass().getCanonicalName();
                BDDAssertions.fail(TYPE_FAIL_MESSAGE.formatted("Key", actualType));
            }
            return result;
        }
    }
}
