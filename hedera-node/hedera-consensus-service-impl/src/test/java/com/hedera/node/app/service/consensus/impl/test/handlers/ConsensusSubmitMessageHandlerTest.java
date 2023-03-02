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
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.txnFrom;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_SCENARIO;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.config.ConsensusServiceConfig;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusSubmitMessageHandlerTest extends ConsensusHandlerTestBase {
    @Mock
    private AccountAccess keyLookup;

    @Mock
    private HandleContext handleContext;

    @Mock
    private MerkleTopic merkleTopic;

    private ConsensusSubmitMessageRecordBuilder recordBuilder;
    private ConsensusServiceConfig config;

    private ConsensusSubmitMessageHandler subject;

    @BeforeEach
    void setUp() {
        commonSetUp();
        subject = new ConsensusSubmitMessageHandler();
        config = new ConsensusServiceConfig(10L, 100);
        recordBuilder = subject.newRecordBuilder();
    }

    @Test
    @DisplayName("Topic submission key sig required")
    void submissionKeySigRequired() {
        readableStore = mock(ReadableTopicStore.class);
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A);
        final var context = new PreHandleContext(keyLookup, newSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, readableStore);

        // then:
        assertOkResponse(context);
        assertThat(context.getPayerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(SIMPLE_KEY_A).orElseThrow();
        assertThat(context.getRequiredNonPayerKeys()).containsExactly(expectedHederaAdminKey);
    }

    @Test
    @DisplayName("Topic not found returns error")
    void topicIdNotFound() {
        mockPayerLookup();
        readableTopicState = emptyReadableTopicState();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        final var context = new PreHandleContext(keyLookup, newSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

        subject.preHandle(context, readableStore);

        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_TOPIC_ID);
        assertThat(context.failed()).isTrue();
    }

    @Test
    @DisplayName("Returns error when payer not found")
    void payerNotFound() {
        readableStore = mock(ReadableTopicStore.class);

        given(keyLookup.getKey((AccountID) notNull()))
                .willReturn(KeyOrLookupFailureReason.withFailureReason(
                        ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST)); // Any error response code
        mockTopicLookup(SIMPLE_KEY_A);
        final var context = new PreHandleContext(keyLookup, newSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

        subject.preHandle(context, readableStore);

        assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_PAYER_ACCOUNT_ID);
        assertThat(context.failed()).isTrue();
        assertThat(context.getPayerKey()).isNull();
    }

    @Test
    @DisplayName("Topic without submit key does not error")
    void noTopicSubmitKey() {
        readableStore = mock(ReadableTopicStore.class);
        mockPayerLookup();
        mockTopicLookup(null);
        final var context = new PreHandleContext(keyLookup, newSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

        // when:
        subject.preHandle(context, readableStore);

        // then:
        assertOkResponse(context);
    }

    @Nested
    class ConsensusSubmitMessageHandlerParityTest {
        @BeforeEach
        void setUp() {
            readableStore = mock(ReadableTopicStore.class);
            keyLookup = com.hedera.node.app.service.consensus.impl.handlers.test.AdapterUtils.wellKnownKeyLookupAt();
        }

        @Test
        void getsConsensusSubmitMessageNoSubmitKey() {
            final var txn = txnFrom(CONSENSUS_SUBMIT_MESSAGE_SCENARIO);

            var topicMeta = newTopicMeta(null);
            given(readableStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta(topicMeta));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, readableStore);

            // then:
            assertOkResponse(context);
            assertDefaultPayer(context);
            assertThat(context.getRequiredNonPayerKeys()).isEmpty();
        }

        @Test
        void getsConsensusSubmitMessageWithSubmitKey() {
            final var txn = txnFrom(CONSENSUS_SUBMIT_MESSAGE_SCENARIO);

            var topicMeta = newTopicMeta(A_NONNULL_KEY);
            given(readableStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withTopicMeta(topicMeta));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, readableStore);

            // then:
            ConsensusTestUtils.assertOkResponse(context);
            ConsensusTestUtils.assertDefaultPayer(context);
            Assertions.assertThat(context.getRequiredNonPayerKeys()).isEqualTo(List.of(A_NONNULL_KEY));
        }

        @Test
        void reportsConsensusSubmitMessageMissingTopic() {
            // given:
            final var txn = txnFrom(CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO);

            given(readableStore.getTopicMetadata(notNull()))
                    .willReturn(ReadableTopicStore.TopicMetaOrLookupFailureReason.withFailureReason(
                            ResponseCodeEnum.INVALID_TOPIC_ID));
            final var context = new PreHandleContext(keyLookup, txn, DEFAULT_PAYER);

            // when:
            subject.preHandle(context, readableStore);

            // then:
            Assertions.assertThat(context.failed()).isTrue();
            Assertions.assertThat(context.getStatus()).isEqualTo(ResponseCodeEnum.INVALID_TOPIC_ID);
        }
    }

    @Test
    @DisplayName("Correct RecordBuilder type returned")
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusSubmitMessageRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        givenValidTopic();
        final var txn = newSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();
        given(handleContext.consensusNow()).willReturn(consensusTimestamp);

        writableTopicState = writableTopicStateWithOneKey();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);

        final var initialTopic = writableTopicState.get(topicEntityNum);
        subject.handle(handleContext, txn, config, recordBuilder, writableStore);

        final var expectedTopic = writableTopicState.get(topicEntityNum);
        assertNotEquals(initialTopic, expectedTopic);
        assertEquals(initialTopic.sequenceNumber() + 1, expectedTopic.sequenceNumber());
        assertNotEquals(
                initialTopic.runningHash().toString(),
                expectedTopic.runningHash().toString());
    }

    @Test
    @DisplayName("Handle fails if submit message is empty")
    void failsIfMessageIsEmpty() {
        givenValidTopic();
        final var txn = newEmptyMessageSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();

        writableTopicState = writableTopicStateWithOneKey();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_TOPIC_MESSAGE, msg.getStatus());
    }

    private HederaKey mockPayerLookup() {
        return ConsensusTestUtils.mockPayerLookup(KeyUtils.A_COMPLEX_KEY, DEFAULT_PAYER, keyLookup);
    }

    private void mockTopicLookup(Key submitKey) {
        ConsensusTestUtils.mockTopicLookup(null, submitKey, readableStore);
    }

    private static ReadableTopicStore.TopicMetadata newTopicMeta(HederaKey submit) {
        return ConsensusTestUtils.newTopicMeta(null, submit);
    }

    private TransactionBody newSubmitMessageTxn(final EntityNum topicEntityNum) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_4).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(TopicID.newBuilder()
                        .setTopicNum(topicEntityNum.longValue())
                        .build())
                .setMessage(ByteString.copyFromUtf8("Message for test-" + Instant.now() + "."
                        + Instant.now().getNano()));
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }

    private TransactionBody newEmptyMessageSubmitMessageTxn(final EntityNum topicEntityNum) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_4).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(TopicID.newBuilder()
                        .setTopicNum(topicEntityNum.longValue())
                        .build())
                .setMessage(ByteString.copyFromUtf8(""));
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }
}
