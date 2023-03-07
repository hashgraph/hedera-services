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

import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.ACCOUNT_ID_4;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.A_NONNULL_KEY;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.newTopicMeta;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.txnFrom;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.EXISTING_TOPIC;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_TOPIC_ADMIN_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusDeleteTopicRecordBuilder;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusDeleteTopicHandlerTest {
    private AccountAccess keyLookup;
    private ReadableTopicStore topicStore;

    private ConsensusDeleteTopicHandler subject;

    @BeforeEach
    void setUp() {
        keyLookup = mock(AccountAccess.class);
        topicStore = mock(ReadableTopicStore.class);
        subject = new ConsensusDeleteTopicHandler();
    }

    @Test
    @DisplayName("Topic admin key sig required")
    void adminKeySigRequired() {
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A, null);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn(), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, topicStore);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(SIMPLE_KEY_A).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(expectedHederaAdminKey);
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusDeleteTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Non-null topic submit key sig is NOT required")
    void submitKeyNotRequired() {
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A, SIMPLE_KEY_B);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn(), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, topicStore);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var unwantedHederaSubmitKey = Utils.asHederaKey(SIMPLE_KEY_B).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).doesNotContain(unwantedHederaSubmitKey);
    }

    @Test
    @DisplayName("Topic not found returns error")
    void topicIdNotFound() {
        // given:
        mockPayerLookup();
        given(topicStore.getTopicMetadata(notNull()))
                .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.INVALID_TOPIC_ID));
        final var context = new PreHandleContext(keyLookup, newDeleteTxn(), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, topicStore);

        // then:
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_TOPIC_ID);
        assertThat(context.failed()).isTrue();
    }

    @Test
    @DisplayName("Returns error when payer not found")
    void payerNotFound() {
        // given:
        given(keyLookup.getKey((AccountID) notNull()))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.ACCOUNT_DELETED)); // Any error response code
        mockTopicLookup(SIMPLE_KEY_A, SIMPLE_KEY_B);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn(), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, topicStore);

        // then:
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        assertThat(context.failed()).isTrue();
        assertThat(context.getPayerKey()).isNull();
    }

    @Test
    @DisplayName("Topic without admin key returns error")
    void noTopicAdminKey() {
        // given:
        mockPayerLookup();
        mockTopicLookup(null, SIMPLE_KEY_A);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn(), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, topicStore);

        // then:
        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(context.failed()).isTrue();
    }

    @Nested
    class ConsensusDeleteTopicHandlerParityTest {
        @BeforeEach
        void setUp() {
            topicStore = mock(ReadableTopicStore.class);
            keyLookup = com.hedera.node.app.service.consensus.impl.handlers.test.AdapterUtils.wellKnownKeyLookupAt();
        }

        @Test
        void getsConsensusDeleteTopicNoAdminKey() {
            // given:
            final var txn = txnFrom(CONSENSUS_DELETE_TOPIC_SCENARIO);

            var topicMeta = newTopicMeta(null, A_NONNULL_KEY); // any submit key that isn't null
            given(topicStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta(topicMeta));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, topicStore);

            // then:
            Assertions.assertThat(context.failed()).isTrue();
            Assertions.assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        }

        @Test
        void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
            // given:
            final var txn = txnFrom(CONSENSUS_DELETE_TOPIC_SCENARIO);
            var topicMeta = newTopicMeta(MISC_TOPIC_ADMIN_KT.asJKey(), null); // any submit key
            given(topicStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta(topicMeta));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, topicStore);

            // then:
            assertOkResponse(context);
            assertDefaultPayer(context);
            Assertions.assertThat(sanityRestored(context.getRequiredNonPayerKeys()))
                    .containsExactly(MISC_TOPIC_ADMIN_KT.asKey());
        }

        @Test
        void reportsConsensusDeleteTopicMissingTopic() {
            // given:
            final var txn = txnFrom(CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO);
            given(topicStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withFailureReason(
                            ResponseCodeEnum.INVALID_TOPIC_ID));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, topicStore);

            // then:
            Assertions.assertThat(context.failed()).isTrue();
            Assertions.assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_TOPIC_ID);
        }
    }

    private HederaKey mockPayerLookup() {
        return ConsensusTestUtils.mockPayerLookup(KeyUtils.A_COMPLEX_KEY, DEFAULT_PAYER, keyLookup);
    }

    private void mockTopicLookup(final Key adminKey, final Key submitKey) {
        ConsensusTestUtils.mockTopicLookup(adminKey, submitKey, topicStore);
    }

    private static TransactionBody newDeleteTxn() {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_4).build();
        final var deleteTopicBuilder =
                ConsensusDeleteTopicTransactionBody.newBuilder().setTopicID(EXISTING_TOPIC);
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusDeleteTopic(deleteTopicBuilder.build())
                .build();
    }
}
