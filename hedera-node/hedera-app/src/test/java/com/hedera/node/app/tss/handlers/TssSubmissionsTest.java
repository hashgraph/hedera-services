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

package com.hedera.node.app.tss.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static com.swirlds.common.PlatformStatus.BEHIND;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssVoteTransactionBody;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TssSubmissionsTest {
    private static final int TIMES_TO_TRY_SUBMISSION = 3;
    private static final int DISTINCT_TXN_IDS_TO_TRY = 2;
    private static final int NANOS_TO_SKIP_ON_DUPLICATE = 13;
    private static final String RETRY_DELAY = "1ms";
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final AccountID NODE_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(666L).build();
    private static final Configuration TEST_CONFIG = HederaTestConfigBuilder.create()
            .withValue("tss.timesToTrySubmission", TIMES_TO_TRY_SUBMISSION)
            .withValue("tss.distinctTxnIdsToTry", DISTINCT_TXN_IDS_TO_TRY)
            .withValue("tss.retryDelay", RETRY_DELAY)
            .getOrCreateConfig();
    private static final Duration DURATION =
            new Duration(TEST_CONFIG.getConfigData(HederaConfig.class).transactionMaxValidDuration());

    @Mock
    private HandleContext context;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private AppContext.Gossip gossip;

    private TssSubmissions subject;

    @BeforeEach
    void setUp() {
        subject = new TssSubmissions(gossip, ForkJoinPool.commonPool());
        given(context.consensusNow()).willReturn(CONSENSUS_NOW);
        given(context.networkInfo()).willReturn(networkInfo);
        given(networkInfo.selfNodeInfo()).willReturn(nodeInfo);
        given(nodeInfo.accountId()).willReturn(NODE_ACCOUNT_ID);
        given(context.configuration()).willReturn(TEST_CONFIG);
    }

    @Test
    void futureResolvesOnSuccessfulSubmission() throws ExecutionException, InterruptedException, TimeoutException {
        final var future = subject.submitTssMessage(TssMessageTransactionBody.DEFAULT, context);

        future.get(1, TimeUnit.SECONDS);

        verify(gossip).submit(messageSubmission(0));
    }

    @Test
    void futureCompletesExceptionallyAfterRetriesExhausted()
            throws ExecutionException, InterruptedException, TimeoutException {
        doThrow(new IllegalStateException("" + BEHIND)).when(gossip).submit(any());

        final var future = subject.submitTssVote(TssVoteTransactionBody.DEFAULT, context);

        future.exceptionally(t -> {
                    verify(gossip, times(TIMES_TO_TRY_SUBMISSION)).submit(voteSubmission(0));
                    return null;
                })
                .get(1, TimeUnit.SECONDS);
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    void immediatelyRetriesOnDuplicateIae() throws ExecutionException, InterruptedException, TimeoutException {
        doThrow(new IllegalArgumentException("" + DUPLICATE_TRANSACTION))
                .when(gossip)
                .submit(voteSubmission(0));

        final var future = subject.submitTssVote(TssVoteTransactionBody.DEFAULT, context);

        future.get(1, TimeUnit.SECONDS);

        verify(gossip).submit(voteSubmission(NANOS_TO_SKIP_ON_DUPLICATE));
    }

    @Test
    void failsImmediatelyOnHittingNonDuplicateIae() throws ExecutionException, InterruptedException, TimeoutException {
        doThrow(new IllegalArgumentException("" + DUPLICATE_TRANSACTION))
                .when(gossip)
                .submit(messageSubmission(0));
        doThrow(new IllegalArgumentException("" + INVALID_NODE_ACCOUNT))
                .when(gossip)
                .submit(messageSubmission(NANOS_TO_SKIP_ON_DUPLICATE));

        final var future = subject.submitTssMessage(TssMessageTransactionBody.DEFAULT, context);

        future.exceptionally(t -> {
                    for (int i = 0; i < DISTINCT_TXN_IDS_TO_TRY; i++) {
                        verify(gossip).submit(messageSubmission(i * NANOS_TO_SKIP_ON_DUPLICATE));
                    }
                    return null;
                })
                .get(1, TimeUnit.SECONDS);
        assertTrue(future.isCompletedExceptionally());
    }

    private TransactionBody voteSubmission(final int nanoOffset) {
        return builderFor(nanoOffset).tssVote(TssVoteTransactionBody.DEFAULT).build();
    }

    private TransactionBody messageSubmission(final int nanoOffset) {
        return builderFor(nanoOffset)
                .tssMessage(TssMessageTransactionBody.DEFAULT)
                .build();
    }

    private TransactionBody.Builder builderFor(final int nanoOffset) {
        return TransactionBody.newBuilder()
                .nodeAccountID(NODE_ACCOUNT_ID)
                .transactionValidDuration(DURATION)
                .transactionID(TransactionID.newBuilder()
                        .accountID(NODE_ACCOUNT_ID)
                        .transactionValidStart(asTimestamp(CONSENSUS_NOW.plusNanos(nanoOffset)))
                        .build());
    }
}
