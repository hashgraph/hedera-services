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

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.handle.TokenContextImpl;
import com.hedera.node.app.workflows.handle.flow.dispatch.user.UserRecordInitializer;
import com.hedera.node.app.workflows.handle.metric.HandleWorkflowMetrics;
import com.hedera.node.app.workflows.handle.record.GenesisWorkflow;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.state.spi.info.NodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BUSY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FAIL_INVALID;
import static com.hedera.node.app.workflows.handle.flow.dispatch.child.helpers.ChildRecordBuilderFactoryTest.asTxn;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserTxnWorkflowTest {
    private static final long CREATOR_NODE_ID = 1L;
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);
    private static final Instant SAME_SECOND_LAST_TIME = CONSENSUS_NOW.minusNanos(889);
    private static final Instant PREV_SECOND_LAST_TIME = CONSENSUS_NOW.minusNanos(891);
    private static final AccountID PAYER_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(1_234).build();
    private static final CryptoTransferTransactionBody TRANSFER_BODY = CryptoTransferTransactionBody.newBuilder()
            .transfers(TransferList.newBuilder()
                    .accountAmounts(
                            AccountAmount.newBuilder()
                                    .accountID(
                                            AccountID.newBuilder().accountNum(1).build())
                                    .amount(0)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .accountID(
                                            AccountID.newBuilder().accountNum(2).build())
                                    .amount(10)
                                    .build()))
            .build();
    private static final TransactionBody TXN_BODY = asTxn(TRANSFER_BODY, PAYER_ACCOUNT_ID, CONSENSUS_NOW);
    private static final TransactionInfo CRYPTO_TRANSFER_TXN_INFO = new TransactionInfo(
            Transaction.newBuilder().body(TXN_BODY).build(),
            TXN_BODY,
            SignatureMap.DEFAULT,
            Bytes.EMPTY,
            CRYPTO_TRANSFER);
    private static final SoftwareVersion PREVIOUS_VERSION = new HederaSoftwareVersion(
            SemanticVersion.DEFAULT, SemanticVersion.newBuilder().major(99).build(), 0);
    private static final SoftwareVersion CURRENT_VERSION = new HederaSoftwareVersion(
            SemanticVersion.DEFAULT, SemanticVersion.newBuilder().major(100).build(), 0);
    private static final SingleTransactionRecord FAKE_RECORD = new SingleTransactionRecord(
            Transaction.newBuilder().body(TXN_BODY).build(),
            TransactionRecord.DEFAULT,
            Collections.emptyList(),
            new SingleTransactionRecord.TransactionOutputs(null));

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private DefaultHandleWorkflow defaultHandleWorkflow;

    @Mock
    private GenesisWorkflow genesisWorkflow;

    @Mock
    private TokenContextImpl tokenContext;

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

    @Mock
    private ExchangeRateSet exchangeRateSet;

    private UserTxnWorkflow subject;

    @Test
    void skipsOlderVersionIfNotEventStreamRecovery() {
        givenSubjectWith(InitTrigger.RESTART, PREVIOUS_VERSION);
        givenNoFailInvalid();
//        given(userTxn.recordListBuilder()).willReturn(new RecordListBuilder(CONSENSUS_NOW));
//        given(userTxn.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        given(exchangeRateManager.exchangeRates()).willReturn(exchangeRateSet);

        final var recordStream = subject.execute();

        final var records = recordStream.toList();
        assertEquals(1, records.size());
        assertEquals(
                Transaction.newBuilder().body(TXN_BODY).build(),
                records.getFirst().transaction());
        assertEquals(BUSY, records.getFirst().transactionRecord().receipt().status());
    }

    @Test
    void executesWorkflowForOlderVersionIfEventStreamRecovery() {
        givenSubjectWith(InitTrigger.EVENT_STREAM_RECOVERY, PREVIOUS_VERSION);
        givenNoFailInvalid();
        givenLastHandled(SAME_SECOND_LAST_TIME);

        final var records = subject.execute();

//        verify(defaultHandleWorkflow).execute(userTxn);
        assertExpected(records);
    }

    @Test
    void alsoExecutesGenesisWorkflowForFirstTransaction() {
        givenSubjectWith(InitTrigger.GENESIS, CURRENT_VERSION);
        givenNoFailInvalid();
//        given(userTxn.isGenesisTxn()).willReturn(true);
//        given(userTxn.tokenContext()).willReturn(tokenContext);

        final var records = subject.execute();

        verify(genesisWorkflow).executeIn(tokenContext);
//        verify(defaultHandleWorkflow).execute(userTxn);
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
//        verify(defaultHandleWorkflow).execute(userTxn);
        verify(handleWorkflowMetrics).switchConsensusSecond();
        assertExpected(records);
    }

    @Test
    void recoversFromUnhandledException() {
//        given(userTxn.consensusNow()).willReturn(CONSENSUS_NOW);
        givenSubjectWith(InitTrigger.RESTART, CURRENT_VERSION);
        givenFailInvalid();
//        doThrow(IllegalStateException.class).when(defaultHandleWorkflow).execute(userTxn);
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

    private void givenSubjectWith(@NonNull final InitTrigger trigger, @NonNull final SoftwareVersion eventVersion) {
        subject = new UserTxnWorkflow(
                CURRENT_VERSION,
                trigger,
                defaultHandleWorkflow,
                genesisWorkflow,
                recordCache,
                handleWorkflowMetrics,
                userRecordInitializer,
                exchangeRateManager,
                null);
        if (trigger != InitTrigger.EVENT_STREAM_RECOVERY) {
            given(consensusEvent.getSoftwareVersion()).willReturn(eventVersion);
//            given(userTxn.platformEvent()).willReturn(consensusEvent);
        }
//        lenient().when(userTxn.consensusNow()).thenReturn(CONSENSUS_NOW);
//        lenient().when(userTxn.stack()).thenReturn(stack);
    }

    private void givenNoFailInvalid() {
//        given(userTxn.recordListBuilder()).willReturn(recordListBuilder);
//        given(userTxn.creator()).willReturn(nodeInfo);
        given(nodeInfo.nodeId()).willReturn(CREATOR_NODE_ID);
//        given(userTxn.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
        lenient()
                .when(recordListBuilder.build())
                .thenReturn(new RecordListBuilder.Result(FAKE_RECORD, List.of(FAKE_RECORD)));
    }

    private void givenFailInvalid() {
//        given(userTxn.creator()).willReturn(nodeInfo);
//        given(userTxn.stack()).willReturn(stack);
        given(nodeInfo.nodeId()).willReturn(CREATOR_NODE_ID);
//        given(userTxn.txnInfo()).willReturn(CRYPTO_TRANSFER_TXN_INFO);
    }

    private void givenLastHandled(@NonNull final Instant lastConsensusTime) {
//        given(userTxn.consensusNow()).willReturn(CONSENSUS_NOW);
//        given(userTxn.lastHandledConsensusTime()).willReturn(lastConsensusTime);
    }

    private void assertExpected(@NonNull final Stream<SingleTransactionRecord> recordStream) {
        final var records = recordStream.toList();
        assertEquals(1, records.size());
        assertEquals(FAKE_RECORD, records.getFirst());
    }
}
