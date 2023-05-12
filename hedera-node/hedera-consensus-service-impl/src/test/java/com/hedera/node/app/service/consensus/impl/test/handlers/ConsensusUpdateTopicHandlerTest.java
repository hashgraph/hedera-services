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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusUpdateTopicRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusUpdateTopicHandlerTest extends ConsensusHandlerTestBase {

    private final ConsensusUpdateTopicTransactionBody.Builder OP_BUILDER =
            ConsensusUpdateTopicTransactionBody.newBuilder();

    private final ExpiryMeta currentExpiryMeta =
            new ExpiryMeta(expirationTime, autoRenewSecs, autoRenewId.accountNum());

    @Mock
    private HandleContext handleContext;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private Account account;

    @Mock
    private Account autoRenewAccount;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private AttributeValidator attributeValidator;

    private final ConsensusUpdateTopicHandler subject = new ConsensusUpdateTopicHandler();

    @Test
    @DisplayName("Correct RecordBuilder type returned")
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusUpdateTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Update a missing topic ID fails")
    void rejectsMissingTopic() {
        final var op = OP_BUILDER.build();

        // expect:
        assertFailsWith(INVALID_TOPIC_ID, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update a deleted topic ID fails")
    void rejectsDeletedTopic() {
        givenValidTopic(0, true);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).build();

        // expect:
        assertFailsWith(INVALID_TOPIC_ID, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("No admin key to update memo fails")
    void rejectsNonExpiryMutationOfImmutableTopic() {
        givenValidTopic(0, false, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).memo("Please mind the vase").build();

        // expect:
        assertFailsWith(ResponseCodeEnum.UNAUTHORIZED, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Invalid new admin key update fails")
    void validatesNewAdminKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).adminKey(key).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleException(ResponseCodeEnum.BAD_ENCODING))
                .given(attributeValidator)
                .validateKey(key);

        // expect:
        assertFailsWith(ResponseCodeEnum.BAD_ENCODING, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update admin key as expected")
    void appliesNewAdminKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).adminKey(anotherKey).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(anotherKey, newTopic.adminKey());
    }

    @Test
    @DisplayName("Invalid new submit key update fails")
    void validatesNewSubmitKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).submitKey(key).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleException(ResponseCodeEnum.BAD_ENCODING))
                .given(attributeValidator)
                .validateKey(key);

        // expect:
        assertFailsWith(ResponseCodeEnum.BAD_ENCODING, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update submit key as expected")
    void appliesNewSubmitKey() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).submitKey(anotherKey).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(anotherKey, newTopic.submitKey());
    }

    @Test
    @DisplayName("Too long memo update fails")
    void validatesNewMemo() {
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).memo("Please mind the vase").build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        willThrow(new HandleException(ResponseCodeEnum.MEMO_TOO_LONG))
                .given(attributeValidator)
                .validateMemo(op.memo());

        // expect:
        assertFailsWith(ResponseCodeEnum.MEMO_TOO_LONG, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update memo as expected")
    void appliesNewMemo() {
        final var newMemo = "Please mind the vase";
        givenValidTopic(0, false);
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).memo(newMemo).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(newMemo, newTopic.memo());
    }

    @Test
    @DisplayName("ExpiryMeta validation fails")
    void validatesNewExpiryViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var expiry = Timestamp.newBuilder().seconds(123L).build();
        final var op = OP_BUILDER.topicID(topicId).expirationTime(expiry).build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        final var impliedMeta = new ExpiryMeta(123L, NA, NA, NA, NA);
        willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(
                ResponseCodeEnum.INVALID_EXPIRATION_TIME, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update expiry as expected")
    void appliesNewExpiryViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var expiry = Timestamp.newBuilder().seconds(123L).build();
        final var op = OP_BUILDER.topicID(topicId).expirationTime(expiry).build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        final var impliedMeta = new ExpiryMeta(123L, NA, NA, NA, NA);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(
                        new ExpiryMeta(123L, currentExpiryMeta.autoRenewPeriod(), currentExpiryMeta.autoRenewNum()));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(123L, newTopic.expiry());
    }

    @Test
    @DisplayName("Invalid new auto renew period update fails")
    void validatesNewAutoRenewPeriodViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var autoRenewPeriod = Duration.newBuilder().seconds(123L).build();
        final var op =
                OP_BUILDER.topicID(topicId).autoRenewPeriod(autoRenewPeriod).build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        final var impliedMeta = new ExpiryMeta(NA, 123L, NA, NA, NA);
        willThrow(new HandleException(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(
                ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE,
                () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update auto renew period as expected")
    void appliesNewAutoRenewPeriodViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();
        final var autoRenewPeriod = Duration.newBuilder().seconds(123L).build();
        final var op =
                OP_BUILDER.topicID(topicId).autoRenewPeriod(autoRenewPeriod).build();
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        final var impliedMeta = new ExpiryMeta(NA, 123L, NA, NA, NA);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(new ExpiryMeta(currentExpiryMeta.expiry(), 123L, currentExpiryMeta.autoRenewNum()));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(123L, newTopic.autoRenewPeriod());
    }

    @Test
    @DisplayName("Invalid new auto renew account update fails")
    void validatesNewAutoRenewAccountViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var op = OP_BUILDER.topicID(topicId).autoRenewAccount(autoRenewId).build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        final var impliedMeta = new ExpiryMeta(NA, NA, autoRenewId.accountNum());
        willThrow(new HandleException(INVALID_AUTORENEW_ACCOUNT))
                .given(expiryValidator)
                .resolveUpdateAttempt(currentExpiryMeta, impliedMeta);

        // expect:
        assertFailsWith(INVALID_AUTORENEW_ACCOUNT, () -> subject.handle(handleContext, op, writableStore));
    }

    @Test
    @DisplayName("Update auto renew account as expected")
    void appliesNewAutoRenewNumViaMeta() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        final var autoRenewAccount = AccountID.newBuilder().accountNum(666).build();
        final var op =
                OP_BUILDER.topicID(topicId).autoRenewAccount(autoRenewAccount).build();
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        final var impliedMeta = new ExpiryMeta(NA, NA, 0, 0, 666);
        given(expiryValidator.resolveUpdateAttempt(currentExpiryMeta, impliedMeta))
                .willReturn(new ExpiryMeta(currentExpiryMeta.expiry(), currentExpiryMeta.autoRenewPeriod(), 666));

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(666L, newTopic.autoRenewAccountNumber());
    }

    @Test
    @DisplayName("Topic is not updated if no fields are changed")
    void nothingHappensIfUpdateIsNoop() {
        refreshStoresWithCurrentTopicInBothReadableAndWritable();

        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        // No-op
        final var op = OP_BUILDER.topicID(topicId).build();

        subject.handle(handleContext, op, writableStore);

        final var newTopic = writableTopicState.get(topicEntityNum);
        assertEquals(topic, newTopic);
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField memo as expected")
    void memoMutationsIsNonExpiry() {
        final var op = OP_BUILDER.memo("HI").build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField adminKey as expected")
    void adminKeyMutationIsNonExpiry() {
        final var op = OP_BUILDER.adminKey(key).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField submitKey as expected")
    void submitKeyMutationIsNonExpiry() {
        final var op = OP_BUILDER.submitKey(key).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField autoRenewPeriod as expected")
    void autoRenewPeriodMutationIsNonExpiry() {
        final var autoRenewPeriod = Duration.newBuilder().seconds(123L).build();
        final var op = OP_BUILDER.autoRenewPeriod(autoRenewPeriod).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField autoRenew account as expected")
    void autoRenewAccountMutationIsNonExpiry() {
        final var op = OP_BUILDER.autoRenewAccount(autoRenewId).build();
        assertTrue(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("Validate Mutate NonExpiryField expireTime as expected")
    void expiryMutationIsExpiry() {
        final var expiryTime = Timestamp.newBuilder().seconds(123L).build();
        final var op = OP_BUILDER.expirationTime(expiryTime).build();
        assertFalse(ConsensusUpdateTopicHandler.wantsToMutateNonExpiryField(op));
    }

    @Test
    @DisplayName("payer key as admin key is allowed")
    void noneOfFieldsSetHaveNoRequiredKeys() throws PreCheckException {
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(adminKey);

        final var op = OP_BUILDER
                .expirationTime(Timestamp.newBuilder().build())
                .topicID(TopicID.newBuilder()
                        .topicNum(topicEntityNum.longValue())
                        .build())
                .build();
        final var context = new FakePreHandleContext(accountStore, txnWith(op));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertDoesNotThrow(() -> subject.preHandle(context));

        assertThat(context.payerKey()).isEqualTo(adminKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Missing topicId from ReadableTopicStore fails")
    void missingTopicFails() throws PreCheckException {
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(adminKey);

        final var op =
                OP_BUILDER.topicID(TopicID.newBuilder().topicNum(123L).build()).build();
        final var context = new FakePreHandleContext(accountStore, txnWith(op));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_TOPIC_ID);
    }

    @Test
    @DisplayName("Update admin key in preHandle as expected")
    void adminKeyAndOpAdminKeyAdded() throws PreCheckException {
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(adminKey);

        final var op = OP_BUILDER
                .adminKey(anotherKey)
                .topicID(TopicID.newBuilder().topicNum(1L).build())
                .build();
        final var context = new FakePreHandleContext(accountStore, txnWith(op));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertDoesNotThrow(() -> subject.preHandle(context));

        assertThat(context.payerKey()).isEqualTo(adminKey);
        // adminKey is same as payer key. So will not be added to required keys.
        // and op admin key is different, so will be added.
        assertEquals(1, context.requiredNonPayerKeys().size());
        assertTrue(context.requiredNonPayerKeys().contains(anotherKey));
    }

    @Test
    @DisplayName("Update autoRenewAccount in preHandle as expected")
    void autoRenewAccountKeyAdded() throws PreCheckException {
        given(accountStore.getAccountById(autoRenewId)).willReturn(autoRenewAccount);
        given(autoRenewAccount.key()).willReturn(autoRenewKey);
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(adminKey);

        final var op = OP_BUILDER.autoRenewAccount(autoRenewId).topicID(topicId).build();
        final var context = new FakePreHandleContext(accountStore, txnWith(op));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertDoesNotThrow(() -> subject.preHandle(context));

        assertThat(context.payerKey()).isEqualTo(adminKey);
        // auto-renew key
        assertEquals(1, context.requiredNonPayerKeys().size());
    }

    @Test
    @DisplayName("Missing autoRenewAccount update fails")
    void missingAutoRenewAccountFails() throws PreCheckException {
        given(accountStore.getAccountById(autoRenewId)).willReturn(null);
        given(accountStore.getAccountById(payerId)).willReturn(account);
        given(account.key()).willReturn(adminKey);

        final var op = OP_BUILDER.autoRenewAccount(autoRenewId).topicID(topicId).build();

        final var context = new FakePreHandleContext(accountStore, txnWith(op));
        context.registerStore(ReadableTopicStore.class, readableStore);

        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    private void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    private TransactionBody txnWith(final ConsensusUpdateTopicTransactionBody op) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId))
                .consensusUpdateTopic(op)
                .build();
    }
}
