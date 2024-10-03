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
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.hedera.hapi.node.base.TopicAllowanceId;
import com.hedera.hapi.node.base.TopicAllowanceValue;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.ConsensusApproveAllowanceTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.consensus.impl.WritableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusApproveAllowanceHandler;
import com.hedera.node.app.service.consensus.impl.validators.ConsensusAllowancesValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
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
        subject = new ConsensusApproveAllowanceHandler(new ConsensusAllowancesValidator());
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

        subject.handle(handleContext);

        final var topicAllowance = writableAllowanceStore.get(defaultAllowanceId());
        final var topicFtAllowance = writableAllowanceStore.get(defaultFtAllowanceId());
        assertNotNull(topicAllowance);
        assertNotNull(topicFtAllowance);

        assertThat(topicAllowance.amount()).isEqualTo(100);
        assertThat(topicAllowance.amountPerMessage()).isEqualTo(10);
        assertThat(topicFtAllowance.amount()).isEqualTo(100);
        assertThat(topicFtAllowance.amountPerMessage()).isEqualTo(10);
    }

    @Test
    void handleWithZeroAllowanceShouldRemoveAllowanceFromStore() {
        setUpStores(handleContext);
        // Arrange
        writableAllowanceStore.put(
                defaultAllowanceId(),
                TopicAllowanceValue.newBuilder().amountPerMessage(5).amount(50).build());
        writableAllowanceStore.put(
                defaultFtAllowanceId(),
                TopicAllowanceValue.newBuilder().amountPerMessage(5).amount(50).build());

        // Create an allowance transaction with amount 0 (which should remove the allowance)
        ConsensusCryptoFeeScheduleAllowance zeroCryptoAllowance = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(ownerId)
                .amount(0L) // Passing 0 to remove the allowance
                .amountPerMessage(0L)
                .topicId(topicId)
                .build();
        ConsensusTokenFeeScheduleAllowance zeroTokenAllowance = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(ownerId)
                .amount(0L) // Passing 0 to remove the allowance
                .amountPerMessage(0L)
                .topicId(topicId)
                .tokenId(fungibleTokenId)
                .build();
        var allowanceTxnBody = consensusApproveAllowanceTransaction(
                ownerId, List.of(zeroCryptoAllowance), List.of(zeroTokenAllowance));

        when(handleContext.body()).thenReturn(allowanceTxnBody);

        // Act
        subject.handle(handleContext);

        // Assert
        var cryptoAllowance = writableAllowanceStore.get(defaultAllowanceId());
        var tokenAllowance = writableAllowanceStore.get(defaultFtAllowanceId());
        assertNull(cryptoAllowance);
        assertNull(tokenAllowance);
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

    private TopicAllowanceId defaultAllowanceId() {
        return TopicAllowanceId.newBuilder().owner(ownerId).topicId(topicId).build();
    }

    private TopicAllowanceId defaultFtAllowanceId() {
        return TopicAllowanceId.newBuilder()
                .owner(ownerId)
                .topicId(topicId)
                .tokenId(fungibleTokenId)
                .build();
    }
}
