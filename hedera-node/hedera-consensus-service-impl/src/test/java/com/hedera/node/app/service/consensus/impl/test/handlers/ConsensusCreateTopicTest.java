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
import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusCreateTopicTest extends ConsensusTestBase {

    @Mock
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock
    private AttributeValidator validator;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private ConsensusCreateTopicRecordBuilder recordBuilder;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private FeeAccumulator feeAccumulator;

    private WritableTopicStore topicStore;
    private ConsensusCreateTopicHandler subject;

    private TransactionBody newCreateTxn(Key adminKey, Key submitKey, boolean hasAutoRenewAccount) {
        final var txnId = TransactionID.newBuilder().accountID(payerId).build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.adminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.submitKey(submitKey);
        }
        createTopicBuilder.autoRenewPeriod(WELL_KNOWN_AUTO_RENEW_PERIOD);
        createTopicBuilder.memo(memo);
        if (hasAutoRenewAccount) {
            createTopicBuilder.autoRenewAccount(autoRenewId);
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
        final var config = HederaTestConfigBuilder.create()
                .withValue("topics.maxNumber", 10L)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);
        given(handleContext.writableStore(WritableTopicStore.class)).willReturn(topicStore);
        given(handleContext.recordBuilder(ConsensusCreateTopicRecordBuilder.class))
                .willReturn(recordBuilder);
        lenient().when(handleContext.feeCalculator(any(SubType.class))).thenReturn(feeCalculator);
        lenient().when(handleContext.feeAccumulator()).thenReturn(feeAccumulator);
        lenient().when(feeCalculator.calculate()).thenReturn(Fees.FREE);
        lenient().when(feeCalculator.legacyCalculate(any())).thenReturn(Fees.FREE);
    }

    @Test
    @DisplayName("All non-null key inputs required")
    void nonNullKeyInputsRequired() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup(key);
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(adminKey, submitKey, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).containsExactlyInAnyOrder(adminKey);
    }

    @Test
    @DisplayName("Non-payer admin key is added")
    void differentAdminKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup(key);
        final var adminKey = SIMPLE_KEY_A;

        // when:
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(adminKey, null, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEqualTo(Set.of(adminKey));
    }

    @Test
    @DisplayName("Non-payer submit key is added")
    void createAddsDifferentSubmitKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup(key);
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(null, submitKey, false));
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
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(protoPayerKey, null, false));
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
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(null, protoPayerKey, false));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Fails if auto account is returned with a null key")
    void autoRenewAccountKeyIsNull() throws PreCheckException {
        // given:
        mockPayerLookup(key);
        given(accountStore.getAccountById(autoRenewId)).willReturn(null);
        final var inputTxn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payerId).build())
                .consensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder()
                        .autoRenewAccount(autoRenewId)
                        .build())
                .build();

        // when:
        final var context = new FakePreHandleContext(accountStore, inputTxn);
        assertThrowsPreCheck(() -> subject.preHandle(context), INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    @DisplayName("Only payer key is always required")
    void requiresPayerKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup(key);
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(null, null, false));

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
        final var txnBody = newCreateTxn(adminKey, submitKey, true);
        final var op = txnBody.consensusCreateTopic();
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), anyBoolean()))
                .willReturn(new ExpiryMeta(
                        1_234_567L + op.autoRenewPeriod().seconds(),
                        op.autoRenewPeriod().seconds(),
                        op.autoRenewAccount()));
        given(handleContext.newEntityNum()).willReturn(1_234L);

        subject.handle(handleContext);

        final var createdTopic =
                topicStore.get(TopicID.newBuilder().topicNum(1_234L).build());
        assertTrue(createdTopic.isPresent());

        final var actualTopic = createdTopic.get();
        assertEquals(0L, actualTopic.sequenceNumber());
        assertEquals(memo, actualTopic.memo());
        assertEquals(adminKey, actualTopic.adminKey());
        assertEquals(submitKey, actualTopic.submitKey());
        assertEquals(1234667, actualTopic.expirationSecond());
        assertEquals(op.autoRenewPeriod().seconds(), actualTopic.autoRenewPeriod());
        assertEquals(autoRenewId, actualTopic.autoRenewAccountId());
        final var topicID = TopicID.newBuilder().topicNum(1_234L).build();
        verify(recordBuilder).topicID(topicID);
        assertTrue(topicStore.get(TopicID.newBuilder().topicNum(1_234L).build()).isPresent());
    }

    @Test
    @DisplayName("Handle works as expected without keys")
    void handleDoesntRequireKeys() {
        final var txnBody = newCreateTxn(null, null, true);
        final var op = txnBody.consensusCreateTopic();
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), anyBoolean()))
                .willReturn(new ExpiryMeta(
                        1_234_567L + op.autoRenewPeriod().seconds(),
                        op.autoRenewPeriod().seconds(),
                        op.autoRenewAccount()));
        given(handleContext.newEntityNum()).willReturn(1_234L);

        subject.handle(handleContext);

        final var createdTopic =
                topicStore.get(TopicID.newBuilder().topicNum(1_234L).build());
        assertTrue(createdTopic.isPresent());

        final var actualTopic = createdTopic.get();
        assertEquals(0L, actualTopic.sequenceNumber());
        assertEquals(memo, actualTopic.memo());
        assertNull(actualTopic.adminKey());
        assertNull(actualTopic.submitKey());
        assertEquals(1_234_567L + op.autoRenewPeriod().seconds(), actualTopic.expirationSecond());
        assertEquals(op.autoRenewPeriod().seconds(), actualTopic.autoRenewPeriod());
        assertEquals(autoRenewId, actualTopic.autoRenewAccountId());
        final var topicID = TopicID.newBuilder().topicNum(1_234L).build();
        verify(recordBuilder).topicID(topicID);
        assertTrue(topicStore.get(TopicID.newBuilder().topicNum(1_234L).build()).isPresent());
    }

    @Test
    @DisplayName("Translates INVALID_EXPIRATION_TIME to AUTO_RENEW_DURATION_NOT_IN_RANGE")
    void translatesInvalidExpiryException() {
        final var txnBody = newCreateTxn(null, null, true);
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), anyBoolean()))
                .willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME));

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, msg.getStatus());
    }

    @Test
    @DisplayName("Doesnt translate INVALID_AUTORENEW_ACCOUNT")
    void doesntTranslateInvalidAutoRenewNum() {
        final var txnBody = newCreateTxn(null, null, true);
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), anyBoolean()))
                .willThrow(new HandleException(INVALID_AUTORENEW_ACCOUNT));

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_AUTORENEW_ACCOUNT, msg.getStatus());
    }

    @Test
    @DisplayName("Memo Validation Failure will throw")
    void handleThrowsIfAttributeValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var txnBody = newCreateTxn(adminKey, submitKey, true);
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleException(ResponseCodeEnum.MEMO_TOO_LONG))
                .when(validator)
                .validateMemo(txnBody.consensusCreateTopicOrThrow().memo());

        assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(0, topicStore.sizeOfState());
    }

    @Test
    @DisplayName("Key Validation Failure will throw")
    void handleThrowsIfKeyValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var txnBody = newCreateTxn(adminKey, submitKey, true);
        given(handleContext.body()).willReturn(txnBody);

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleException(ResponseCodeEnum.BAD_ENCODING))
                .when(validator)
                .validateKey(adminKey);
        assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(0, topicStore.sizeOfState());
    }

    @Test
    @DisplayName("Fails when the allowed topics are already created")
    void failsWhenMaxRegimeExceeds() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var txnBody = newCreateTxn(adminKey, submitKey, true);
        given(handleContext.body()).willReturn(txnBody);
        final var writableState = writableTopicStateWithOneKey();

        given(writableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());
        given(handleContext.writableStore(WritableTopicStore.class)).willReturn(topicStore);

        given(handleContext.attributeValidator()).willReturn(validator);
        final var config = HederaTestConfigBuilder.create()
                .withValue("topics.maxNumber", 1L)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(config);

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, msg.getStatus());
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    @Test
    @DisplayName("Validates AutoRenewAccount")
    void validatedAutoRenewAccount() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var txnBody = newCreateTxn(adminKey, submitKey, true);
        given(handleContext.body()).willReturn(txnBody);
        final var writableState = writableTopicStateWithOneKey();

        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(writableStates.<TopicID, Topic>get(TOPICS_KEY)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());

        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        doThrow(HandleException.class).when(expiryValidator).resolveCreationAttempt(anyBoolean(), any(), anyBoolean());

        final var failure = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    // Note: there are more tests in ConsensusCreateTopicHandlerParityTest.java

    private Key mockPayerLookup(Key key) {
        final var account = mock(Account.class);
        given(account.key()).willReturn(key);
        given(accountStore.getAccountById(payerId)).willReturn(account);
        return key;
    }
}
