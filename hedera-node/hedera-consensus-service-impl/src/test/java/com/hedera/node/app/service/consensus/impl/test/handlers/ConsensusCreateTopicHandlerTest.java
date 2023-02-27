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

import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountLookup;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.node.app.spi.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
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
class ConsensusCreateTopicHandlerTest {
    private static final AccountID ACCOUNT_ID_3 = IdUtils.asAccount("0.0.3");

    private final ConsensusServiceConfig consensusConfig = new ConsensusServiceConfig(1234L, 5678);

    @Mock
    private AccountLookup keyFinder;

    @Mock
    private HandleContext handleContext;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private ConsensusCreateTopicRecordBuilder recordBuilder;

    private ConsensusCreateTopicHandler subject;

    private static TransactionBody newCreateTxn(Key adminKey, Key submitKey) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build();
        final var createTopicBuilder = ConsensusCreateTopicTransactionBody.newBuilder();
        if (adminKey != null) {
            createTopicBuilder.setAdminKey(adminKey);
        }
        if (submitKey != null) {
            createTopicBuilder.setSubmitKey(submitKey);
        }
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusCreateTopic(createTopicBuilder.build())
                .build();
    }

    @BeforeEach
    void setUp() {
        subject = new ConsensusCreateTopicHandler();
    }

    @Test
    @DisplayName("All non-null key inputs required")
    void nonNullKeyInputsRequired() {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(adminKey, submitKey), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(expectedHederaAdminKey, expectedHederaSubmitKey);
    }

    @Test
    @DisplayName("Non-payer admin key is added")
    void differentAdminKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(adminKey, null), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).isEqualTo(List.of(expectedHederaAdminKey));
    }

    @Test
    @DisplayName("Non-payer submit key is added")
    void createAddsDifferentSubmitKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, submitKey), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(expectedHederaSubmitKey);
    }

    @Test
    @DisplayName("Payer key can be added as admin")
    void createAddsPayerAsAdmin() {
        // given:
        final var protoPayerKey = SIMPLE_KEY_A;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var context = new PreHandleContext(keyFinder, newCreateTxn(protoPayerKey, null), ACCOUNT_ID_3);
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
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, protoPayerKey), ACCOUNT_ID_3);
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(payerKey);
    }

    @Test
    @DisplayName("Fails if payer is not found")
    void createFailsWhenPayerNotFound() {
        // given:
        given(keyFinder.getKey((AccountID) any()))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = newCreateTxn(null, null);

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
        final var context = new PreHandleContext(keyFinder, newCreateTxn(null, null), ACCOUNT_ID_3);

        // when:
        subject.preHandle(context);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        assertThat(context.getRequiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Handle method not implemented")
    void handleNotImplemented() {
        // expect:
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.handle(handleContext, transactionBody, consensusConfig, recordBuilder));
    }

    // Note: there are more tests in ConsensusCreateTopicHandlerParityTest.java

    private HederaKey mockPayerLookup() {
        return mockPayerLookup(KeyUtils.A_COMPLEX_KEY);
    }

    private HederaKey mockPayerLookup(Key key) {
        final var returnKey = Utils.asHederaKey(key).orElseThrow();
        given(keyFinder.getKey(ACCOUNT_ID_3)).willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }
}
