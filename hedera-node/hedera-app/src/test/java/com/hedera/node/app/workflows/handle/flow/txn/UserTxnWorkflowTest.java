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

package com.hedera.node.app.workflows.handle.flow.txn;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.logic.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserTxnWorkflowTest {
    private static final long CREATOR_NODE_ID = 1L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant SAME_SECOND_LAST_TIME = CONSENSUS_NOW.minusNanos(889);
    private static final Instant PREV_SECOND_LAST_TIME = CONSENSUS_NOW.minusNanos(891);
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final TransactionBody TXN_BODY = TransactionBody.newBuilder()
            .transactionID(
                    TransactionID.newBuilder().accountID(PAYER_ACCOUNT_ID).build())
            .build();
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO =
            new TransactionInfo(Transaction.DEFAULT, TXN_BODY, SignatureMap.DEFAULT, Bytes.EMPTY, CRYPTO_TRANSFER);
    private static final SemanticVersion PREVIOUS_VERSION = SemanticVersion.newBuilder().major(99).build();
    private static final SemanticVersion CURRENT_VERSION = SemanticVersion.newBuilder().major(100).build();
    private static final SingleTransactionRecord FAKE_RECORD = new SingleTransactionRecord(
            Transaction.DEFAULT,
            TransactionRecord.DEFAULT,
            Collections.emptyList(),
            new SingleTransactionRecord.TransactionOutputs(null));

    @Mock
    private SkipHandleWorkflow skipHandleWorkflow;

    @Mock
    private DefaultHandleWorkflow defaultHandleWorkflow;

    @Mock
    private GenesisWorkflow genesisWorkflow;

    @Mock
    private UserTransactionComponent userTxn;

    @Mock
    private TokenContext tokenContext;

    @Mock
    private HederaRecordCache recordCache;

    @Mock
    private HandleWorkflowMetrics handleWorkflowMetrics;

    @Mock
    private UserRecordInitializer userRecordInitializer;

    @Mock
    private RecordListBuilder recordListBuilder;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private ConsensusEvent consensusEvent;

    private UserTxnWorkflow subject;

    @Test
    void skipsOlderVersionIfNotEventStreamRecovery() {
        givenSubjectWith(InitTrigger.RESTART, PREVIOUS_VERSION);
        givenNoFailInvalid();

        final var records = subject.execute();

        verify(skipHandleWorkflow).execute(userTxn);
        assertExpected(records);
    }

    @Test
    void executesWorkflowForOlderVersionIfEventStreamRecovery() {
        givenSubjectWith(InitTrigger.EVENT_STREAM_RECOVERY, PREVIOUS_VERSION);
        givenNoFailInvalid();
        givenLastHandled(SAME_SECOND_LAST_TIME);

        final var records = subject.execute();

        verify(defaultHandleWorkflow).execute(userTxn);
        assertExpected(records);
    }

    @Test
    void alsoExecutesGenesisWorkflowForFirstTransaction() {
        givenSubjectWith(InitTrigger.GENESIS, CURRENT_VERSION);
        givenNoFailInvalid();
        given(userTxn.isGenesisTxn()).willReturn(true);
        given(userTxn.tokenContext()).willReturn(tokenContext);

        final var records = subject.execute();

        verify(genesisWorkflow).executeIn(tokenContext);
        verify(defaultHandleWorkflow).execute(userTxn);
        verify(handleWorkflowMetrics).switchConsensusSecond();
        assertExpected(records);
    }

    @Test
    void executesJustDefaultWorkflowForNonGenesisTransaction() {
        givenSubjectWith(InitTrigger.RESTART, CURRENT_VERSION);
        givenNoFailInvalid();
        givenLastHandled(PREV_SECOND_LAST_TIME);

        final var records = subject.execute();

        verifyNoInteractions(genesisWorkflow);
        verify(defaultHandleWorkflow).execute(userTxn);
        verify(handleWorkflowMetrics).switchConsensusSecond();
        assertExpected(records);
    }

    @Test
    void recoversFromUnhandledException() {
        given(userTxn.consensusNow()).willReturn(CONSENSUS_NOW);
        givenSubjectWith(InitTrigger.RESTART, CURRENT_VERSION);
        givenFailInvalid();
        doThrow(IllegalStateException.class).when(defaultHandleWorkflow).execute(userTxn);
        doAnswer(invocationOnMock -> {
                    final SingleTransactionRecordBuilderImpl recordBuilder = invocationOnMock.getArgument(0);
                    recordBuilder
                            .transaction(CRYPTO_TRANSFER_TXN_INFO.transaction())
                            .transactionID(requireNonNull(CRYPTO_TRANSFER_TXN_INFO.transactionID()));
                    return null;
                })
                .when(userRecordInitializer)
                .initializeUserRecord(any(), any());

        final var records = subject.execute().toList();

        verify(stack).rollbackFullStack();
        verify(userRecordInitializer).initializeUserRecord(any(), eq(CRYPTO_TRANSFER_TXN_INFO));
        assertEquals(1, records.size());
        assertThat(records.getFirst().transactionRecord().receiptOrThrow().status())
                .isEqualTo(FAIL_INVALID);
    }

    private void givenSubjectWith(@NonNull final InitTrigger trigger, @NonNull final SemanticVersion eventVersion) {
        subject = new UserTxnWorkflow(
                CURRENT_VERSION,
                trigger,
                skipHandleWorkflow,
                defaultHandleWorkflow,
                genesisWorkflow,
                userTxn,
                recordCache,
                handleWorkflowMetrics,
                userRecordInitializer);
        if (trigger != InitTrigger.EVENT_STREAM_RECOVERY) {
            given(consensusEvent.getSoftwareVersion()).willReturn(eventVersion);
            given(userTxn.platformEvent()).willReturn(consensusEvent);
        }
    }

    private void givenNoFailInvalid() {
        given(userTxn.recordListBuilder()).willReturn(recordListBuilder);
        given(userTxn.creator()).willReturn(nodeInfo);
        given(nodeInfo.nodeId()).willReturn(CREATOR_NODE_ID);
        given(userTxn.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(recordListBuilder.build()).willReturn(new RecordListBuilder.Result(FAKE_RECORD, List.of(FAKE_RECORD)));
    }

    private void givenFailInvalid() {
        given(userTxn.creator()).willReturn(nodeInfo);
        given(userTxn.stack()).willReturn(stack);
        given(nodeInfo.nodeId()).willReturn(CREATOR_NODE_ID);
        given(userTxn.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
    }

    private void givenLastHandled(@NonNull final Instant lastConsensusTime) {
        given(userTxn.consensusNow()).willReturn(CONSENSUS_NOW);
        given(userTxn.lastHandledConsensusTime()).willReturn(lastConsensusTime);
    }

    private void assertExpected(@NonNull final Stream<SingleTransactionRecord> recordStream) {
        final var records = recordStream.toList();
        assertEquals(1, records.size());
        assertEquals(FAKE_RECORD, records.getFirst());
    }
}
