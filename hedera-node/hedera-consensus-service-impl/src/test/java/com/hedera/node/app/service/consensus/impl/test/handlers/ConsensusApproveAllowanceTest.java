/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ConsensusCryptoFeeScheduleAllowance;
import com.hedera.hapi.node.base.ConsensusTokenFeeScheduleAllowance;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.hapi.node.state.consensus.TopicCryptoAllowance;
import com.hedera.hapi.node.state.consensus.TopicFungibleTokenAllowance;
import com.hedera.hapi.node.token.ConsensusApproveAllowanceTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusApproveAllowanceHandler;
import com.hedera.node.app.service.consensus.impl.ConsensusAllowanceUpdater;
import com.hedera.node.app.service.consensus.impl.validators.ConsensusAllowancesValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ConsensusApproveAllowanceTest extends ConsensusTestBase {
    @Mock(strictness = LENIENT)
    private HandleContext.SavepointStack stack;

    private ConsensusApproveAllowanceHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusApproveAllowanceHandler(new ConsensusAllowancesValidator(), new ConsensusAllowanceUpdater());
        refreshStoresWithCurrentTopicOnlyInReadable();
        given(handleContext.savepointStack()).willReturn(stack);
    }

    @Test
    void preHandleWithValidAllowancesShouldPass() throws PreCheckException {
        // Arrange
        PreHandleContext mockContext = mock(PreHandleContext.class);
        var txnBody = consensusApproveAllowanceTransaction(ownerId, List.of(), List.of());

        when(mockContext.body()).thenReturn(txnBody);
        when(mockContext.payer()).thenReturn(ownerId);

        // Act & Assert: should not throw any exception
        subject.preHandle(mockContext);

        verify(mockContext, times(1)).body();
    }

    @Test
    void handleWithEmptyAllowancesShouldNotUpdateState() {
        // Arrange
        WritableTopicStore mockTopicStore = mock(WritableTopicStore.class);
        var emptyTxnBody = consensusApproveAllowanceTransaction(ownerId, List.of(), List.of());

        given(handleContext.body()).willReturn(emptyTxnBody);

        when(handleContext.storeFactory().writableStore(WritableTopicStore.class))
                .thenReturn(mockTopicStore);

        // Act
        subject.handle(handleContext);

        // Assert
        verify(mockTopicStore, never()).put(any());
    }

    @Test
    void happyPathAddsAllowances() {
        setUpStores(handleContext);
        final var txn = consensusApproveAllowanceTransaction(
                payerId, List.of(consensusCryptoAllowance()), List.of(consensusTokenAllowance()));
        given(handleContext.body()).willReturn(txn);
        final var topic = writableStore.getTopic(topicId);
        assertNotNull(topic);
        assertThat(topic.cryptoAllowances()).isEmpty();
        assertThat(topic.tokenAllowances()).isEmpty();

        subject.handle(handleContext);

        final var modifiedTopic = writableStore.getTopic(topicId);
        assertNotNull(modifiedTopic);
        assertThat(modifiedTopic.cryptoAllowances()).hasSize(1);
        assertThat(modifiedTopic.tokenAllowances()).hasSize(1);

        assertThat(modifiedTopic.cryptoAllowances().getFirst().spenderId()).isEqualTo(ownerId);
        assertThat(modifiedTopic.cryptoAllowances().getFirst().amount()).isEqualTo(100);
        assertThat(modifiedTopic.cryptoAllowances().getFirst().amountPerMessage())
                .isEqualTo(10);
        assertThat(modifiedTopic.tokenAllowances().getFirst().spenderId()).isEqualTo(ownerId);
        assertThat(modifiedTopic.tokenAllowances().getFirst().amount()).isEqualTo(100);
        assertThat(modifiedTopic.tokenAllowances().getFirst().amountPerMessage())
                .isEqualTo(10);
        assertThat(modifiedTopic.tokenAllowances().getFirst().tokenId()).isEqualTo(fungibleTokenId);
    }

    @Test
    void handleWithZeroAllowanceShouldRemoveAllowanceFromStore() {
        setUpStores(handleContext);
        // Mock topic store and a topic with an existing allowance
        var topicFromStateId = TopicID.newBuilder().topicNum(1).build();
        List<TopicCryptoAllowance> initialCryptoAllowances = new ArrayList<>();
        List<TopicFungibleTokenAllowance> initialTokenAllowances = new ArrayList<>();

        initialCryptoAllowances.add(TopicCryptoAllowance.newBuilder()
                .spenderId(ownerId)
                .amount(100L)
                .amountPerMessage(10L)
                .build());
        initialTokenAllowances.add(TopicFungibleTokenAllowance.newBuilder()
                .spenderId(ownerId)
                .amount(100L)
                .amountPerMessage(10L)
                .tokenId(fungibleTokenId)
                .build());

        // Add the topic with the initial allowance
        Topic topic = Topic.newBuilder()
                .topicId(topicFromStateId)
                .cryptoAllowances(initialCryptoAllowances)
                .tokenAllowances(initialTokenAllowances)
                .build();
        writableStore.put(topic);

        // Create an allowance transaction with amount 0 (which should remove the allowance)
        ConsensusCryptoFeeScheduleAllowance zeroCryptoAllowance = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(ownerId)
                .amount(0L) // Passing 0 to remove the allowance
                .amountPerMessage(0L)
                .topicId(topicFromStateId)
                .build();
        ConsensusTokenFeeScheduleAllowance zeroTokenAllowance = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(ownerId)
                .amount(0L) // Passing 0 to remove the allowance
                .amountPerMessage(0L)
                .topicId(topicFromStateId)
                .tokenId(fungibleTokenId)
                .build();
        var allowanceTxnBody = consensusApproveAllowanceTransaction(
                ownerId, List.of(zeroCryptoAllowance), List.of(zeroTokenAllowance));

        when(handleContext.body()).thenReturn(allowanceTxnBody);

        // Act
        subject.handle(handleContext);

        // Assert
        Topic updatedTopic = writableStore.getTopic(topicFromStateId);
        assertNotNull(updatedTopic);
        assertTrue(updatedTopic.cryptoAllowances().isEmpty(), "Crypto allowance should be removed from the store");
        assertTrue(updatedTopic.tokenAllowances().isEmpty(), "Token allowance should be removed from the store");
    }

    private TransactionBody consensusApproveAllowanceTransaction(
            final AccountID id,
            final List<ConsensusCryptoFeeScheduleAllowance> cryptoAllowance,
            final List<ConsensusTokenFeeScheduleAllowance> tokenAllowance) {
        final var transactionID = TransactionID.newBuilder().accountID(id);
        final var allowanceTxnBody = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusCryptoFeeScheduleAllowances(cryptoAllowance)
                .consensusTokenFeeScheduleAllowances(tokenAllowance)
                .build();
        return TransactionBody.newBuilder()
                .transactionID(transactionID)
                .consensusApproveAllowance(allowanceTxnBody)
                .build();
    }

    private ConsensusCryptoFeeScheduleAllowance consensusCryptoAllowance() {
        return ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .amount(100)
                .amountPerMessage(10)
                .topicId(topicId)
                .owner(ownerId)
                .build();
    }

    private ConsensusTokenFeeScheduleAllowance consensusTokenAllowance() {
        return ConsensusTokenFeeScheduleAllowance.newBuilder()
                .amount(100)
                .amountPerMessage(10)
                .tokenId(fungibleTokenId)
                .topicId(topicId)
                .owner(ownerId)
                .build();
    }
}
