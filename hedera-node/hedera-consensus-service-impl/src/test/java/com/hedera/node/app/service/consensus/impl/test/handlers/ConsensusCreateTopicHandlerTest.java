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

import static com.hedera.node.app.service.consensus.impl.handlers.TemporaryUtils.fromGrpcKey;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BAD_ENCODING;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.CreateTopicRecordBuilder;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusCreateTopicHandlerTest extends ConsensusHandlerTestBase {
    static final AccountID ACCOUNT_ID_3 = IdUtils.asAccount("0.0.3");
    private static final AccountID AUTO_RENEW_ACCOUNT = IdUtils.asAccount("0.0.4");

    @Mock
    private AccountAccess keyFinder;

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

    private static TransactionBody newCreateTxn(Key adminKey, Key submitKey, boolean hasAutoRenewAccount) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.setSubmitKey(submitKey);
        }
        createTopicBuilder.setAutoRenewPeriod(
                Duration.newBuilder().setSeconds(10000L).build());
        createTopicBuilder.setMemo("memo");
        if (hasAutoRenewAccount) {
            createTopicBuilder.setAutoRenewAccount(AUTO_RENEW_ACCOUNT);
        }
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusCreateTopic(createTopicBuilder.build())
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
    void nonNullKeyInputsRequired() {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(adminKey, submitKey, false), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = asHederaKey(adminKey).orElseThrow();
        final var expectedHederaSubmitKey = asHederaKey(submitKey).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(expectedHederaAdminKey);
    }

    @Test
    @DisplayName("Non-payer admin key is added")
    void differentAdminKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(adminKey, null, false), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = asHederaKey(adminKey).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).isEqualTo(List.of(expectedHederaAdminKey));
    }

    @Test
    @DisplayName("Non-payer submit key is added")
    void createAddsDifferentSubmitKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, submitKey, false), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Payer key can be added as admin")
    void createAddsPayerAsAdmin() {
        // given:
        final var protoPayerKey = SIMPLE_KEY_A;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(protoPayerKey, null, false), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(payerKey);
    }

    @Test
    @DisplayName("Payer key can be added as submitter")
    void createAddsPayerAsSubmitter() {
        // given:
        final var protoPayerKey = SIMPLE_KEY_B;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, protoPayerKey, false), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
    }

    @Test
    @DisplayName("Fails if payer is not found")
    void createFailsWhenPayerNotFound() {
        // given:
        given(keyFinder.getKey((AccountID) any()))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = newCreateTxn(null, null, false);

        // when:
        final var context = new PreHandleContext(keyFinder, inputTxn, IdUtils.asAccount("0.0.1234"));
        subject.preHandle(context);

        // then:
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        assertThat(context.failed()).isTrue();
        assertThat(context.getPayerKey()).isNull();
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Fails if auto account is returned with a null key")
    void autoAccountKeyIsNull() {
        // given:
        mockPayerLookup();
        final var acct1234 = IdUtils.asAccount("0.0.1234");
        given(keyFinder.getKey(acct1234))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build())
                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.newBuilder()
                        .setAutoRenewAccount(acct1234)
                        .build())
                .build();

        // when:
        final var context = new PreHandleContext(keyFinder, inputTxn, ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
        assertThat(context.failed()).isTrue();
    }

    @Test
    @DisplayName("Only payer key is always required")
    void requiresPayerKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, null, false), ACCOUNT_ID_3);

        // when:
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).getConsensusCreateTopic();

        given(handleContext.attributeValidator()).willReturn(validator);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.validateCreationAttempt(anyBoolean(), any()))
                .willReturn(new ExpiryMeta(
                        1_234_567L + op.getAutoRenewPeriod().getSeconds(),
                        op.getAutoRenewPeriod().getSeconds(),
                        op.getAutoRenewAccount().getAccountNum()));
        given(handleContext.newEntityNumSupplier()).willReturn(() -> 1_234L);

        subject.handle(handleContext, op, config, recordBuilder, topicStore);

        final var createdTopic = topicStore.get(1_234L);
        assertTrue(createdTopic.isPresent());

        final var actualTopic = createdTopic.get();
        assertEquals(0L, actualTopic.sequenceNumber());
        assertEquals("memo", actualTopic.memo());
        assertEquals(fromGrpcKey(adminKey), actualTopic.adminKey());
        assertEquals(fromGrpcKey(submitKey), actualTopic.submitKey());
        assertEquals(1244567, actualTopic.expiry());
        assertEquals(10000, actualTopic.autoRenewPeriod());
        assertEquals(AUTO_RENEW_ACCOUNT.getAccountNum(), actualTopic.autoRenewAccountNumber());
        assertEquals(1_234L, recordBuilder.getCreatedTopic());
        assertTrue(topicStore.get(1234L).isPresent());
    }

    @Test
    @DisplayName("Memo Validation Failure will throw")
    void handleThrowsIfAttributeValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).getConsensusCreateTopic();

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleStatusException(MEMO_TOO_LONG)).when(validator).validateMemo(op.getMemo());

        assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertTrue(topicStore.get(1234L).isEmpty());
    }

    @Test
    @DisplayName("Key Validation Failure will throw")
    void handleThrowsIfKeyValidatorFails() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).getConsensusCreateTopic();

        given(handleContext.attributeValidator()).willReturn(validator);

        doThrow(new HandleStatusException(BAD_ENCODING)).when(validator).validateKey(adminKey);
        assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertTrue(topicStore.get(1234L).isEmpty());
    }

    @Test
    @DisplayName("Key Validation Failure will throw")
    void failsWhenMaxRegimeExceeds() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).getConsensusCreateTopic();
        final var writableState = writableTopicStateWithOneKey();

        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());

        given(handleContext.attributeValidator()).willReturn(validator);
        config = new ConsensusServiceConfig(1, 1);

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, msg.getStatus());
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    @Test
    @DisplayName("Validates AutoRenewAccount")
    void validatedAutoRenewAccount() {
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;
        final var op = newCreateTxn(adminKey, submitKey, true).getConsensusCreateTopic();
        final var writableState = writableTopicStateWithOneKey();

        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableState);
        final var topicStore = new WritableTopicStore(writableStates);
        assertEquals(1, topicStore.sizeOfState());

        given(handleContext.attributeValidator()).willReturn(validator);
        config = new ConsensusServiceConfig(10, 100);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        doThrow(HandleStatusException.class).when(expiryValidator).validateCreationAttempt(anyBoolean(), any());

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, op, config, recordBuilder, topicStore));
        assertEquals(HandleStatusException.class, msg.getClass());
        assertEquals(0, topicStore.modifiedTopics().size());
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusCreateTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    // Note: there are more tests in ConsensusCreateTopicHandlerParityTest.java

    private HederaKey mockPayerLookup() {
        return mockPayerLookup(KeyUtils.A_COMPLEX_KEY);
    }

    private HederaKey mockPayerLookup(Key key) {
        final var returnKey = asHederaKey(key).orElseThrow();
        given(keyFinder.getKey(ACCOUNT_ID_3)).willReturn(withKey(returnKey));
        return returnKey;
    }
}
