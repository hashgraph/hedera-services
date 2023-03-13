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

import static com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler.noThrowSha384HashOf;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusCreateTopicHandlerTest.ACCOUNT_ID_3;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.ACCOUNT_ID_4;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.A_NONNULL_KEY;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertOkResponse;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.txnFrom;
import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.service.mono.state.merkle.MerkleTopic.RUNNING_HASH_VERSION;
import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusSubmitMessageScenarios.CONSENSUS_SUBMIT_MESSAGE_SCENARIO;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.exceptions.HandleStatusException;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.HandleContext;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.utils.KeyUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusMessageChunkInfo;
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

    private ConsensusSubmitMessageRecordBuilder recordBuilder;
    private ConsensusServiceConfig config;

    private ConsensusSubmitMessageHandler subject;

    @BeforeEach
    void setUp() {
        commonSetUp();
        subject = new ConsensusSubmitMessageHandler();
        config = new ConsensusServiceConfig(10L, 100);
        recordBuilder = subject.newRecordBuilder();

        writableTopicState = writableTopicStateWithOneKey();
        given(readableStates.<EntityNum, Topic>get(TOPICS)).willReturn(readableTopicState);
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        readableStore = new ReadableTopicStore(readableStates);
        writableStore = new WritableTopicStore(writableStates);
    }

    @Test
    @DisplayName("Topic submission key sig required")
    void submissionKeySigRequired() {
        readableStore = mock(ReadableTopicStore.class);
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A);
        final var context = new PreHandleContext(keyLookup, newDefaultSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

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
        final var context = new PreHandleContext(keyLookup, newDefaultSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

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
        final var context = new PreHandleContext(keyLookup, newDefaultSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

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
        final var context = new PreHandleContext(keyLookup, newDefaultSubmitMessageTxn(topicEntityNum), DEFAULT_PAYER);

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
        final var txn = newDefaultSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();
        given(handleContext.consensusNow()).willReturn(consensusTimestamp);

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
    @DisplayName("Handle works as expected if Consensus time is null")
    void handleWorksAsExpectedIfConsensusTimeIsNull() {
        givenValidTopic();
        final var txn = newDefaultSubmitMessageTxn(topicEntityNum);

        final var recordBuilder = subject.newRecordBuilder();
        given(handleContext.consensusNow()).willReturn(null);

        final var initialTopic = writableTopicState.get(topicEntityNum);
        subject.handle(handleContext, txn, config, recordBuilder, writableStore);

        final var expectedTopic = writableTopicState.get(topicEntityNum);
        assertNotEquals(initialTopic, expectedTopic);
        assertEquals(initialTopic.sequenceNumber() + 1, expectedTopic.sequenceNumber());
        assertNotEquals(
                initialTopic.runningHash().toString(),
                expectedTopic.runningHash().toString());
        assertArrayEquals(asBytes(expectedTopic.runningHash()), recordBuilder.getNewTopicRunningHash());
        assertEquals(expectedTopic.sequenceNumber(), recordBuilder.getNewTopicSequenceNumber());
        assertEquals(RUNNING_HASH_VERSION, recordBuilder.getUsedRunningHashVersion());
    }

    @Test
    @DisplayName("Handle fails if submit message is empty")
    void failsIfMessageIsEmpty() {
        givenValidTopic();
        final var txn = newSubmitMessageTxn(topicEntityNum, "");

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_TOPIC_MESSAGE, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message is too large")
    void failsIfMessageIsTooLarge() {
        givenValidTopic();
        final var txn = newSubmitMessageTxn(
                topicEntityNum, TxnUtils.randomUtf8Bytes(2000).toString());

        final var recordBuilder = subject.newRecordBuilder();
        config = new ConsensusServiceConfig(10, 5);

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.MESSAGE_SIZE_TOO_LARGE, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if topic for which message is being submitted is not found")
    void failsIfTopicIDInvalid() {
        givenValidTopic();
        final var txn = newDefaultSubmitMessageTxn(MISSING_NUM);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_TOPIC_ID, msg.getStatus());
    }

    @Test
    void failsOnUnavailableDigest() {
        final var raw = NONSENSE.toByteArray();
        assertDoesNotThrow(() -> noThrowSha384HashOf(raw));
    }

    @Test
    @DisplayName("Handle fails if submit message chunk number is invalid")
    void failsIfChunkNumberIsInvalid() {
        givenValidTopic();
        final var txn = newSubmitMessageTxnWithChunks(topicEntityNum, 2, 1);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_NUMBER, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk number is less than 1")
    void failsIfChunkNumberInvalid() {
        givenValidTopic();
        final var txn = newSubmitMessageTxnWithChunks(topicEntityNum, 0, 1);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_NUMBER, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk txn payer is not same as initial txn payer")
    void failsIfChunkTxnPayerIsNotInitialPayer() {
        givenValidTopic();
        final var chunkTxnId =
                TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build();
        final var txn = newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, 2, 2, chunkTxnId);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Handle fails if submit message chunk txn payer is not same as initial txn payer")
    void failsIfChunkTxnPayerIsNotInitialID() {
        givenValidTopic();
        final var chunkTxnId =
                TransactionID.newBuilder().setAccountID(ACCOUNT_ID_3).build();
        final var txn = newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, 1, 2, chunkTxnId);

        final var recordBuilder = subject.newRecordBuilder();

        final var msg = assertThrows(
                HandleStatusException.class,
                () -> subject.handle(handleContext, txn, config, recordBuilder, writableStore));
        assertEquals(ResponseCodeEnum.INVALID_CHUNK_TRANSACTION_ID, msg.getStatus());
    }

    /* ----------------- Helper Methods ------------------- */

    private HederaKey mockPayerLookup() {
        return ConsensusTestUtils.mockPayerLookup(KeyUtils.A_COMPLEX_KEY, DEFAULT_PAYER, keyLookup);
    }

    private void mockTopicLookup(Key submitKey) {
        ConsensusTestUtils.mockTopicLookup(null, submitKey, readableStore);
    }

    private static ReadableTopicStore.TopicMetadata newTopicMeta(HederaKey submit) {
        return ConsensusTestUtils.newTopicMeta(null, submit);
    }

    private TransactionBody newDefaultSubmitMessageTxn(final EntityNum topicEntityNum) {
        return newSubmitMessageTxn(
                topicEntityNum,
                "Message for test-" + Instant.now() + "." + Instant.now().getNano());
    }

    private TransactionBody newSubmitMessageTxn(final EntityNum topicEntityNum, final String message) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_4).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(TopicID.newBuilder()
                        .setTopicNum(topicEntityNum.longValue())
                        .build())
                .setMessage(ByteString.copyFromUtf8(message));
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }

    private TransactionBody newSubmitMessageTxnWithChunks(
            final EntityNum topicEntityNum, final int currentChunk, final int totalChunk) {
        return newSubmitMessageTxnWithChunksAndPayer(topicEntityNum, currentChunk, totalChunk, null);
    }

    private TransactionBody newSubmitMessageTxnWithChunksAndPayer(
            final EntityNum topicEntityNum,
            final int currentChunk,
            final int totalChunk,
            final TransactionID initialTxnId) {
        final var txnId = TransactionID.newBuilder().setAccountID(ACCOUNT_ID_4).build();
        final var submitMessageBuilder = ConsensusSubmitMessageTransactionBody.newBuilder()
                .setTopicID(TopicID.newBuilder()
                        .setTopicNum(topicEntityNum.longValue())
                        .build())
                .setChunkInfo(ConsensusMessageChunkInfo.newBuilder()
                        .setInitialTransactionID(initialTxnId != null ? initialTxnId : txnId)
                        .setNumber(currentChunk)
                        .setTotal(totalChunk)
                        .build())
                .setMessage(ByteString.copyFromUtf8("test"));
        return TransactionBody.newBuilder()
                .setTransactionID(txnId)
                .setConsensusSubmitMessage(submitMessageBuilder.build())
                .build();
    }

    private static final ByteString NONSENSE = ByteString.copyFromUtf8("NONSENSE");
}
