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
package com.hedera.node.app.service.consensus.impl.handlers.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.List;
import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusCreateTopicHandlerTest {
    private static final AccountID ACCOUNT_ID_3 = IdUtils.asAccount("0.0.3");
    private static final Key SIMPLE_KEY_A =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFrom("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
                    .build();
    private static final Key SIMPLE_KEY_B =
            Key.newBuilder()
                    .setEd25519(ByteString.copyFrom("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))
                    .build();

    @Mock private AccountKeyLookup keyFinder;

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

    static void assertOkResponse(TransactionMetadata result) {
        assertThat(result.status()).isEqualTo(ResponseCodeEnum.OK);
        assertThat(result.failed()).isFalse();
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
        final var result =
                subject.preHandle(newCreateTxn(adminKey, submitKey), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        assertThat(result.requiredNonPayerKeys())
                .containsExactly(expectedHederaAdminKey, expectedHederaSubmitKey);
    }

    @Test
    @DisplayName("Non-payer admin key is added")
    void differentAdminKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var adminKey = SIMPLE_KEY_A;

        // when:
        final var result = subject.preHandle(newCreateTxn(adminKey, null), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(adminKey).orElseThrow();
        assertThat(result.requiredNonPayerKeys()).isEqualTo(List.of(expectedHederaAdminKey));
    }

    @Test
    @DisplayName("Non-payer submit key is added")
    void createAddsDifferentSubmitKey() {
        // given:
        final var payerKey = mockPayerLookup();
        final var submitKey = SIMPLE_KEY_B;

        // when:
        final var result =
                subject.preHandle(newCreateTxn(null, submitKey), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        final var expectedHederaSubmitKey = Utils.asHederaKey(submitKey).orElseThrow();
        assertThat(result.requiredNonPayerKeys()).containsExactly(expectedHederaSubmitKey);
    }

    @Test
    @DisplayName("Payer key can be added as admin")
    void createAddsPayerAsAdmin() {
        // given:
        final var protoPayerKey = SIMPLE_KEY_A;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var result =
                subject.preHandle(newCreateTxn(protoPayerKey, null), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        assertThat(result.requiredNonPayerKeys()).containsExactly(payerKey);
    }

    @Test
    @DisplayName("Payer key can be added as submitter")
    void createAddsPayerAsSubmitter() {
        // given:
        final var protoPayerKey = SIMPLE_KEY_B;
        final var payerKey = mockPayerLookup(protoPayerKey);

        // when:
        final var result =
                subject.preHandle(newCreateTxn(null, protoPayerKey), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        assertThat(result.requiredNonPayerKeys()).containsExactly(payerKey);
    }

    @Test
    @DisplayName("Fails if payer is not found")
    void createFailsWhenPayerNotFound() {
        // given:
        given(keyFinder.getKey(any()))
                .willReturn(
                        KeyOrLookupFailureReason.withFailureReason(
                                ResponseCodeEnum
                                        .ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        final var inputTxn = newCreateTxn(null, null);

        // when:
        final var result = subject.preHandle(inputTxn, IdUtils.asAccount("0.0.1234"), keyFinder);

        // then:
        assertThat(result.status()).isEqualTo(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        assertThat(result.failed()).isTrue();
        assertThat(result.payerKey()).isNull();
        assertThat(result.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Fails if payer is null")
    void nullPayer() {
        // given:
        final var inputTxn = newCreateTxn(SIMPLE_KEY_A, SIMPLE_KEY_B);

        // when/then:
        //noinspection DataFlowIssue : explicitly passing null as a test param
        final ThrowableAssert.ThrowingCallable toRun =
                () -> subject.preHandle(inputTxn, null, keyFinder);
        assertThatThrownBy(toRun).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Only payer key is always required")
    void requiresPayerKey() {
        // given:
        final var payerKey = mockPayerLookup();

        // when:
        final var result = subject.preHandle(newCreateTxn(null, null), ACCOUNT_ID_3, keyFinder);

        // then:
        assertOkResponse(result);
        assertThat(result.payerKey()).isEqualTo(payerKey);
        assertThat(result.requiredNonPayerKeys()).isEmpty();
    }

    // Note: there are more tests in ConsensusCreateTopicHandlerParityTest.java

    private HederaKey mockPayerLookup() {
        return mockPayerLookup(KeyUtils.A_COMPLEX_KEY);
    }

    private HederaKey mockPayerLookup(Key key) {
        final var returnKey = Utils.asHederaKey(key).orElseThrow();
        given(keyFinder.getKey(ACCOUNT_ID_3))
                .willReturn(KeyOrLookupFailureReason.withKey(returnKey));
        return returnKey;
    }
}
