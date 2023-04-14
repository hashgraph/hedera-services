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
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.CreateTopicRecordBuilder;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusCreateTopicHandlerTest extends ConsensusHandlerTestBase {
    static final AccountID ACCOUNT_ID_3 = AccountID.newBuilder().accountNum(3L).build();
    private static final AccountID AUTO_RENEW_ACCOUNT =
            AccountID.newBuilder().accountNum(4L).build();

    @Mock
    private AccountAccess accountAccess;

    @Mock
    private HandleContext handleContext;

    @Mock
    private AttributeValidator validator;

    @Mock
    private ExpiryValidator expiryValidator;

    private ConsensusCreateTopicRecordBuilder recordBuilder;
    private ConsensusServiceConfig config;

    private WritableTopicStore topicStore;
    private ConsensusCreateTopicHandler subject;

    private TransactionBody newCreateTxn(Key adminKey, Key submitKey, boolean hasAutoRenewAccount) {
        final var txnId = TransactionID.newBuilder().accountID(ACCOUNT_ID_3).build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.adminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.submitKey(submitKey);
        }
        createTopicBuilder.autoRenewPeriod(WELL_KNOWN_AUTO_RENEW_PERIOD);
        createTopicBuilder.memo("memo");
        if (hasAutoRenewAccount) {
            createTopicBuilder.autoRenewAccount(AUTO_RENEW_ACCOUNT);
        }
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .consensusCreateTopic(createTopicBuilder.build())
                .build();
    }

    @BeforeEach
    void setUp() {
        subject = new ConsensusCreateTopicHandler();
        topicStore = new WritableTopicStore(writableStates);
        config = new ConsensusServiceConfig(10L, 100);
        recordBuilder = new CreateTopicRecordBuilder();
    }

    @Test
    @DisplayName("All non-null key inputs required")
    void nonNullKeyInputsRequired() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(accountAccess, newCreateTxn(adminKey, submitKey, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = asHederaKey(adminKey).orElseThrow();
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(expectedHederaAdminKey);
    }

    @Test
    @DisplayName("Non-payer admin key is added")
    void differentAdminKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;

        // when:
        final var context = new PreHandleContext(accountAccess, newCreateTxn(adminKey, null, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = asHederaKey(adminKey).orElseThrow();
        assertThat(context.requiredNonPayerKeys()).isEqualTo(Set.of(expectedHederaAdminKey));
    }

    @Test
    @DisplayName("Non-payer submit key is added")
    void createAddsDifferentSubmitKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(accountAccess, newCreateTxn(null, submitKey, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Payer key can be added as admin")
    void createAddsPayerAsAdmin() throws PreCheckException {
        // given:
        final var protoPayerKey = SIMPLE_KEY_A;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var context = new PreHandleContext(accountAccess, newCreateTxn(protoPayerKey, null, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Payer key can be added as submitter")
    void createAddsPayerAsSubmitter() throws PreCheckException {
        // given:
        final var protoPayerKey = SIMPLE_KEY_B;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var context = new PreHandleContext(accountAccess, newCreateTxn(null, protoPayerKey, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
    }

    @Test
    @DisplayName("Fails if auto account is returned with a null key")
    void autoAccountKeyIsNull() throws PreCheckException {
        // given:
        mockPayerLookup();
        final var acct1234 = AccountID.newBuilder().accountNum(1234).build();
        given(accountAccess.getAccountById(acct1234)).willReturn(null);
        final var inputTxn = TransactionBody.newBuilder()
                .transactionID(
                        TransactionID.newBuilder().accountID(ACCOUNT_ID_3).build())
                .consensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder()
                        .autoRenewAccount(acct1234)
                        .build())
                .build();

        // when:
        final var context = new PreHandleContext(accountAccess, inputTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    @DisplayName("Only payer key is always required")
    void requiresPayerKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var context = new PreHandleContext(accountAccess, newCreateTxn(null, null, false));

        // when:
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).consensusCreateTopicOrThrow();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any()))
                .willReturn(new ExpiryMeta(
                        1_234_567L + op.autoRenewPeriod().seconds(),
                        op.autoRenewPeriod().seconds(),
                        op.autoRenewAccount().accountNum()));
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1_234L);

        subject.handle(handleContext, op, config, recordBuilder, topicStore);

        final var createdTopic = topicStore.get(1_234L);
        assertTrue(createdTopic.isPresent());

        final var actualTopic = createdTopic.get();
        assertEquals(0L, actualTopic.sequenceNumber());
        assertEquals("memo", actualTopic.memo());
        assertEquals(adminKey, actualTopic.adminKey());
        assertEquals(submitKey, actualTopic.submitKey());
        assertEquals(1234667, actualTopic.expiry());
        assertEquals(op.autoRenewPeriod().seconds(), actualTopic.autoRenewPeriod());
        assertEquals(AUTO_RENEW_ACCOUNT.accountNum(), actualTopic.autoRenewAccountNumber());
        assertEquals(1_234L, recordBuilder.getCreatedTopic());
        assertTrue(topicStore.get(1234L).isPresent());
    }

    @Test
    @DisplayName("Handle works as expected without keys")
    void handleDoesntRequireKeys() {
        final var op = newCreateTxn(null, null, true).consensusCreateTopicOrThrow();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any()))
                .willReturn(new ExpiryMeta(
                        1_234_567L + op.autoRenewPeriod().seconds(),
                        op.autoRenewPeriod().seconds(),
                        op.autoRenewAccount().accountNumOrElse(0L)));
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1_234L);

        subject.handle(handleContext, op, config, recordBuilder, topicStore);

        final var createdTopic = topicStore.get(1_234L);
        assertTrue(createdTopic.isPresent());

        final var actualTopic = createdTopic.get();
        assertEquals(0L, actualTopic.sequenceNumber());
        assertEquals("memo", actualTopic.memo());
        assertNull(actualTopic.adminKey());
        assertNull(actualTopic.submitKey());
        assertEquals(1_234_567L + op.autoRenewPeriod().seconds(), actualTopic.expiry());
        assertEquals(op.autoRenewPeriod().seconds(), actualTopic.autoRenewPeriod());
        assertEquals(AUTO_RENEW_ACCOUNT.accountNum(), actualTopic.autoRenewAccountNumber());
        assertEquals(1_234L, recordBuilder.getCreatedTopic());
        assertTrue(topicStore.get(1234L).isPresent());
    }

    @Test
    @DisplayName("Translates INVALID_EXPIRATION_TIME to AUTO_RENEW_DURATION_NOT_IN_RANGE")
    void translatesInvalidExpiryException() {
        final var op = newCreateTxn(null, null, true).consensusCreateTopicOrThrow();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any()))
                .willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME));

        final var failure = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, failure.getStatus());
    }

    @Test
    @DisplayName("Doesnt translate INVALID_AUTORENEW_ACCOUNT")
    void doesntTranslateInvalidAutoRenewNum() {
        final var op = newCreateTxn(null, null, true).consensusCreateTopicOrThrow();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any()))
                .willThrow(new HandleException(INVALID_AUTORENEW_ACCOUNT));

        final var failure = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(INVALID_AUTORENEW_ACCOUNT, failure.getStatus());
    }

    @Test
    @DisplayName("Memo Validation Failure will throw")
    void handleThrowsIfAttributeValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).consensusCreateTopicOrThrow();

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleException(ResponseCodeEnum.MEMO_TOO_LONG))
                .when(validator)
                .validateMemo(op.memo());

        assertThrows(HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertTrue(topicStore.get(1234L).isEmpty());
    }

    @Test
    @DisplayName("Key Validation Failure will throw")
    void handleThrowsIfKeyValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).consensusCreateTopicOrThrow();

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleException(ResponseCodeEnum.BAD_ENCODING))
                .when(validator)
                .validateKey(adminKey);
        assertThrows(HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertTrue(topicStore.get(1234L).isEmpty());
    }

    @Test
    @DisplayName("Fails when the allowed topics are already created")
    void failsWhenMaxRegimeExceeds() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).consensusCreateTopicOrThrow();
        final var writableState = writableTopicStateWithOneKey();

        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());

        given(handleContext.attributeValidator()).willReturn(validator);
        config = new ConsensusServiceConfig(1, 1);

        final var msg = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, msg.getStatus());
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    @Test
    @DisplayName("Validates AutoRenewAccount")
    void validatedAutoRenewAccount() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).consensusCreateTopicOrThrow();
        final var writableState = writableTopicStateWithOneKey();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());

        given(handleContext.attributeValidator()).willReturn(validator);
        config = new ConsensusServiceConfig(10, 100);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        doThrow(HandleException.class).when(expiryValidator).resolveCreationAttempt(anyBoolean(), any());

        final var failure = assertThrows(
                HandleException.class, () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(HandleException.class, failure.getClass());
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusCreateTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    // Note: there are more tests in ConsensusCreateTopicHandlerParityTest.java

    private HederaKey mockPayerLookup() throws PreCheckException {
        return mockPayerLookup(A_COMPLEX_KEY);
    }

    private HederaKey mockPayerLookup(Key key) throws PreCheckException {
        final var returnKey = asHederaKey(key).orElseThrow();
        final var account = mock(Account.class);
        given(account.key()).willReturn(returnKey);
        given(accountAccess.getAccountById(ACCOUNT_ID_3)).willReturn(account);
        return returnKey;
    }
}
