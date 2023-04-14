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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOPIC_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNAUTHORIZED;
import static com.hedera.node.app.service.consensus.impl.test.handlers.AdapterUtils.PARITY_DEFAULT_PAYER;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.A_NONNULL_KEY;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_A;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.SIMPLE_KEY_B;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.assertDefaultPayer;
import static com.hedera.node.app.service.consensus.impl.test.handlers.ConsensusTestUtils.newTopicMeta;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.ConsensusDeleteTopicScenarios.CONSENSUS_DELETE_TOPIC_SCENARIO;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_TOPIC_ADMIN_KT;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.records.ConsensusDeleteTopicRecordBuilder;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusDeleteTopicHandlerTest extends ConsensusHandlerTestBase {
    private AccountAccess keyLookup;
    private ReadableTopicStore mockStore;

    private ConsensusDeleteTopicHandler subject;

    @BeforeEach
    void setUp() {
        keyLookup = mock(AccountAccess.class);
        mockStore = mock(ReadableTopicStore.class);
        subject = new ConsensusDeleteTopicHandler();

        writableTopicState = writableTopicStateWithOneKey();
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        writableStore = new WritableTopicStore(writableStates);
    }

    @Test
    @DisplayName("Topic admin key sig required")
    void adminKeySigRequired() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A, null);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn());

        // when:
        subject.preHandle(context, mockStore);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        final var expectedHederaAdminKey = Utils.asHederaKey(SIMPLE_KEY_A).orElseThrow();
        assertThat(context.requiredNonPayerKeys())
                .containsExactlyInAnyOrder(expectedHederaAdminKey);
    }

    @Test
    void returnsExpectedRecordBuilderType() {
        assertInstanceOf(ConsensusDeleteTopicRecordBuilder.class, subject.newRecordBuilder());
    }

    @Test
    @DisplayName("Non-null topic submit key sig is NOT required")
    void submitKeyNotRequired() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        mockTopicLookup(SIMPLE_KEY_A, SIMPLE_KEY_B);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn());

        // when:
        subject.preHandle(context, mockStore);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        final var unwantedHederaSubmitKey = Utils.asHederaKey(SIMPLE_KEY_B).orElseThrow();
        assertThat(context.requiredNonPayerKeys()).doesNotContain(unwantedHederaSubmitKey);
    }

    @Test
    @DisplayName("Topic not found returns error")
    void topicIdNotFound() throws PreCheckException {
        // given:
        mockPayerLookup();
        given(mockStore.getTopicMetadata(notNull())).willReturn(null);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn());

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context, mockStore), INVALID_TOPIC_ID);
    }

    @Test
    @DisplayName("Topic without admin key returns error")
    void noTopicAdminKey() throws PreCheckException {
        // given:
        mockPayerLookup();
        mockTopicLookup(null, SIMPLE_KEY_A);
        final var context = new PreHandleContext(keyLookup, newDeleteTxn());

        // when:
        assertThrowsPreCheck(() -> subject.preHandle(context, mockStore), UNAUTHORIZED);
    }

    @Test
    @DisplayName("Fails handle if topic doesn't exist")
    void topicDoesntExist() {
        final var txn = newDeleteTxn().consensusDeleteTopicOrThrow();

        writableTopicState = emptyWritableTopicState();
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        writableStore = new WritableTopicStore(writableStates);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(txn, writableStore));
        assertEquals(INVALID_TOPIC_ID, msg.getStatus());
    }

    @Test
    @DisplayName("Fails handle if admin key doesn't exist on topic to be deleted")
    void adminKeyDoesntExist() {
        final var txn = newDeleteTxn().consensusDeleteTopicOrThrow();

        topic = new Topic(
                topicId.topicNum(),
                sequenceNumber,
                expirationTime,
                autoRenewSecs,
                10L,
                false,
                Bytes.wrap(runningHash),
                memo,
                null,
                null);

        writableTopicState = writableTopicStateWithOneKey();
        given(writableStates.<EntityNum, Topic>get(TOPICS)).willReturn(writableTopicState);
        writableStore = new WritableTopicStore(writableStates);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(txn, writableStore));

        assertEquals(ResponseCodeEnum.UNAUTHORIZED, msg.getStatus());
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        final var txn = newDeleteTxn().consensusDeleteTopicOrThrow();

        final var existingTopic = writableStore.get(topicEntityNum.longValue());
        assertTrue(existingTopic.isPresent());
        assertFalse(existingTopic.get().deleted());

        subject.handle(txn, writableStore);

        final var changedTopic = writableStore.get(topicEntityNum.longValue());

        assertTrue(changedTopic.isPresent());
        assertTrue(changedTopic.get().deleted());
    }

    private Key mockPayerLookup() {
        return ConsensusTestUtils.mockPayerLookup(A_COMPLEX_KEY, PARITY_DEFAULT_PAYER, keyLookup);
    }

    private void mockTopicLookup(final Key adminKey, final Key submitKey) throws PreCheckException {
        ConsensusTestUtils.mockTopicLookup(adminKey, submitKey, mockStore);
    }

    private TransactionBody newDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(PARITY_DEFAULT_PAYER).build();
        final var deleteTopicBuilder =
                ConsensusDeleteTopicTransactionBody.newBuilder().topicID(WELL_KNOWN_TOPIC_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .consensusDeleteTopic(deleteTopicBuilder.build())
                .build();
    }

    private HederaKey mockPayerLookup() throws PreCheckException {
        return ConsensusTestUtils.mockPayerLookup(A_COMPLEX_KEY, PARITY_DEFAULT_PAYER, keyLookup);
    }

    private void mockTopicLookup(final Key adminKey, final Key submitKey) throws PreCheckException {
        ConsensusTestUtils.mockTopicLookup(adminKey, submitKey, mockStore);
    }

    private TransactionBody newDeleteTxn() {
        final var txnId = TransactionID.newBuilder().accountID(PARITY_DEFAULT_PAYER).build();
        final var deleteTopicBuilder =
                ConsensusDeleteTopicTransactionBody.newBuilder().topicID(WELL_KNOWN_TOPIC_ID);
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .consensusDeleteTopic(deleteTopicBuilder.build())
                .build();
    }

    @Nested
    class ConsensusDeleteTopicHandlerParityTest {
        @BeforeEach
        void setUp() {
            mockStore = mock(ReadableTopicStore.class);
            keyLookup = AdapterUtils.wellKnownKeyLookupAt();
        }

        @Test
        void getsConsensusDeleteTopicNoAdminKey() throws PreCheckException {
            // given:
            final var txn = CONSENSUS_DELETE_TOPIC_SCENARIO.pbjTxnBody();

            var topicMeta = newTopicMeta(null, A_NONNULL_KEY); // any submit key that isn't null
            given(mockStore.getTopicMetadata(notNull())).willReturn(topicMeta);
            final var context = new PreHandleContext(keyLookup, txn);

            // when:
            assertThrowsPreCheck(() -> subject.preHandle(context, mockStore), UNAUTHORIZED);
        }

        @Test
        void getsConsensusDeleteTopicWithAdminKey() throws Throwable {
            // given:
            final var txn = CONSENSUS_DELETE_TOPIC_SCENARIO.pbjTxnBody();
            var topicMeta = newTopicMeta(MISC_TOPIC_ADMIN_KT.asJKey(), null); // any submit key
            given(mockStore.getTopicMetadata(notNull())).willReturn(topicMeta);
            final var context = new PreHandleContext(keyLookup, txn);

            // when:
            subject.preHandle(context, mockStore);

            // then:
            assertDefaultPayer(context);
            Assertions.assertThat(sanityRestored(context.requiredNonPayerKeys()))
                    .containsExactly(MISC_TOPIC_ADMIN_KT.asKey());
        }

        @Test
        void reportsConsensusDeleteTopicMissingTopic() throws PreCheckException {
            // given:
            final var txn = CONSENSUS_DELETE_TOPIC_MISSING_TOPIC_SCENARIO.pbjTxnBody();
            given(mockStore.getTopicMetadata(notNull())).willReturn(null);
            final var context = new PreHandleContext(keyLookup, txn);

            // when:
            assertThrowsPreCheck(() -> subject.preHandle(context, mockStore), INVALID_TOPIC_ID);
        }
    }
}
