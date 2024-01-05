/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.records;

import static com.hedera.node.app.hapi.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.TxnUtils.assertExhaustsResourceLimit;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CHUNK_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.REVERTED_SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.contracts.execution.TransactionProcessingResult;
import com.hedera.node.app.service.mono.legacy.core.jproto.TxnReceipt;
import com.hedera.node.app.service.mono.state.expiry.ExpiringCreations;
import com.hedera.node.app.service.mono.state.submerkle.CurrencyAdjustments;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.EvmFnResult;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;
import com.hedera.node.app.service.mono.state.submerkle.TxnId;
import com.hedera.node.app.service.mono.stream.RecordStreamObject;
import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.SidecarUtils;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.services.stream.proto.TransactionSidecarRecord;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.crypto.RunningHash;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TxnAwareRecordsHistorianTest {
    private final long submittingMember = 1L;
    private final AccountID a = asAccount("0.0.1111");
    private final EntityId aEntity = EntityId.fromGrpcAccountId(a);
    private final AccountID b = asAccount("0.0.2222");
    private final AccountID c = asAccount("0.0.3333");
    private final AccountID effPayer = asAccount("0.0.5555");
    private final long nows = 1_234_567L;
    private final int nanos = 999_999_999;
    private final Instant topLevelNow = Instant.ofEpochSecond(nows, 999_999_999);
    final int payerRecordTtl = 180;
    final long payerExpiry = topLevelNow.getEpochSecond() + payerRecordTtl;
    private final AccountID d = asAccount("0.0.4444");
    private final TransactionID txnIdA = TransactionID.newBuilder()
            .setTransactionValidStart(Timestamp.newBuilder().setSeconds(nows).setNanos(nanos))
            .setAccountID(a)
            .build();
    private final CurrencyAdjustments initialTransfers = CurrencyAdjustments.fromChanges(
            new long[] {-1_000L, 500L, 501L, 01L},
            new long[] {a.getAccountNum(), b.getAccountNum(), c.getAccountNum(), d.getAccountNum()});
    private final ExpirableTxnRecord.Builder finalRecord = ExpirableTxnRecord.newBuilder()
            .setTxnId(TxnId.fromGrpc(txnIdA))
            .setHbarAdjustments(initialTransfers)
            .setMemo("This is different!")
            .setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build());
    private final ExpirableTxnRecord.Builder jFinalRecord = finalRecord;
    private final ExpirableTxnRecord payerRecord = finalRecord.build();

    {
        payerRecord.setExpiry(payerExpiry);
    }

    @Mock
    private RecordCache recordCache;

    @Mock
    private ExpiringCreations creator;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private RecordStreamObject rso;

    @Mock
    private ConsensusTimeTracker consensusTimeTracker;

    @Mock
    private ExpirableTxnRecord.Builder mockRecordBuilder;

    @Mock
    private FunctionalityThrottling handleThrottling;

    private TxnAwareRecordsHistorian subject;

    @BeforeEach
    void setup() {
        subject = new TxnAwareRecordsHistorian(recordCache, txnCtx, consensusTimeTracker, handleThrottling);
        subject.setCreator(creator);
    }

    @Test
    void lastUsedRunningHashIsLastSucceedingChildIfPreset() {
        final var mockHash = new RunningHash();
        final var mockSubject = mock(RecordsHistorian.class);
        willCallRealMethod().given(mockSubject).lastRunningHash();

        given(mockSubject.hasFollowingChildRecords()).willReturn(true);
        given(mockSubject.getFollowingChildRecords()).willReturn(List.of(new RecordStreamObject(), rso));
        given(rso.getRunningHash()).willReturn(mockHash);

        assertSame(mockHash, mockSubject.lastRunningHash());
    }

    @Test
    void lastUsedRunningHashIsTopLevelIfNoSuccesors() {
        final var mockHash = new RunningHash();
        final var mockSubject = mock(RecordsHistorian.class);
        willCallRealMethod().given(mockSubject).lastRunningHash();

        given(mockSubject.getTopLevelRecord()).willReturn(rso);
        given(rso.getRunningHash()).willReturn(mockHash);

        assertSame(mockHash, mockSubject.lastRunningHash());
    }

    @Test
    void revertsRecordsFromGivenSourceOnly() {
        given(consensusTimeTracker.isAllowableFollowingOffset(1)).willReturn(true);
        given(consensusTimeTracker.isAllowableFollowingOffset(2)).willReturn(true);
        given(consensusTimeTracker.isAllowablePrecedingOffset(1)).willReturn(true);
        given(consensusTimeTracker.isAllowablePrecedingOffset(2)).willReturn(true);

        final var followingRecordFrom1 = mock(ExpirableTxnRecord.Builder.class);
        final var followingRecordFrom2 = mock(ExpirableTxnRecord.Builder.class);
        final var precedingRecordFrom1 = mock(ExpirableTxnRecord.Builder.class);
        final var precedingRecordFrom2 = mock(ExpirableTxnRecord.Builder.class);

        subject.trackFollowingChildRecord(
                1, TransactionBody.newBuilder(), followingRecordFrom1, List.of(TransactionSidecarRecord.newBuilder()));
        subject.trackFollowingChildRecord(
                2, TransactionBody.newBuilder(), followingRecordFrom2, List.of(TransactionSidecarRecord.newBuilder()));
        subject.trackPrecedingChildRecord(1, TransactionBody.newBuilder(), precedingRecordFrom1);
        subject.trackPrecedingChildRecord(2, TransactionBody.newBuilder(), precedingRecordFrom2);
        subject.revertChildRecordsFromSource(2);

        verify(followingRecordFrom1, never()).revert();
        verify(followingRecordFrom2).revert();
        verify(precedingRecordFrom1, never()).revert();
        verify(precedingRecordFrom2).revert();
    }

    @Test
    void ignoresUnsuccessfulChildRecordIfNotExternalizedOnFailure() {
        final var mockFollowingRecord = mock(ExpirableTxnRecord.class);
        final var mockTopLevelRecord = mock(ExpirableTxnRecord.class);
        given(mockTopLevelRecord.getEnumStatus()).willReturn(SUCCESS);
        final var expFollowId = txnIdA.toBuilder().setNonce(1).build();
        final var correctFollowingChildConsTime = RichInstant.fromJava(topLevelNow.plusNanos(1));
        final var placeholderFollowingChildNows = nows + 7;
        given(mockFollowingRecord.getConsensusSecond()).willReturn(placeholderFollowingChildNows);

        final var topLevelRecord = mock(ExpirableTxnRecord.Builder.class);
        given(topLevelRecord.getTxnId()).willReturn(TxnId.fromGrpc(txnIdA));
        final var followingBuilder = mock(ExpirableTxnRecord.Builder.class);
        given(consensusTimeTracker.isAllowableFollowingOffset(1)).willReturn(true);
        final var followingAfterBuilder = mock(ExpirableTxnRecord.Builder.class);
        given(consensusTimeTracker.isAllowableFollowingOffset(2)).willReturn(true);
        given(followingAfterBuilder.build()).willReturn(mockFollowingRecord);

        givenTopLevelContext();
        given(topLevelRecord.setNumChildRecords(anyShort())).willReturn(topLevelRecord);
        given(topLevelRecord.build()).willReturn(mockTopLevelRecord);

        given(creator.saveExpiringRecord(
                        effPayer, mockFollowingRecord, placeholderFollowingChildNows, submittingMember))
                .willReturn(mockFollowingRecord);

        given(txnCtx.recordSoFar()).willReturn(topLevelRecord);
        given(txnCtx.sidecars()).willReturn(List.of(TransactionSidecarRecord.newBuilder()));
        given(creator.saveExpiringRecord(effPayer, mockTopLevelRecord, nows, submittingMember))
                .willReturn(mockTopLevelRecord);

        final var followSynthBody = aBuilderWith("FOLLOW");
        assertEquals(topLevelNow.plusNanos(1), subject.nextFollowingChildConsensusTime());
        subject.trackFollowingChildRecord(
                1, followSynthBody, followingBuilder, List.of(TransactionSidecarRecord.newBuilder()));

        final var followAfterSynthBody = aBuilderWith("FOLLOW_AFTER");
        given(followingAfterBuilder.getTxnId()).willReturn(TxnId.fromGrpc(expFollowId));
        given(mockFollowingRecord.getTxnId()).willReturn(TxnId.fromGrpc(expFollowId));
        assertEquals(topLevelNow.plusNanos(2), subject.nextFollowingChildConsensusTime());
        subject.trackFollowingChildRecord(
                1, followAfterSynthBody, followingAfterBuilder, List.of(TransactionSidecarRecord.newBuilder()));

        given(followingBuilder.shouldNotBeExternalized()).willReturn(true);
        given(txnCtx.accessor()).willReturn(accessor);

        subject.saveExpirableTransactionRecords();
        final var followingRsos = subject.getFollowingChildRecords();
        assertEquals(1, followingRsos.size());
        verify(consensusTimeTracker).setActualFollowingRecordsCount(1L);
        verify(followingAfterBuilder).setConsensusTime(correctFollowingChildConsTime);
    }

    @Test
    void customizesMatchingSuccessor() {
        final var followingBuilder = mock(ExpirableTxnRecord.Builder.class);
        given(txnCtx.consensusTime()).willReturn(topLevelNow);
        given(consensusTimeTracker.isAllowableFollowingOffset(1)).willReturn(true);

        final var followSynthBody = aBuilderWith("FOLLOW");
        assertEquals(topLevelNow.plusNanos(1), subject.nextFollowingChildConsensusTime());
        subject.trackFollowingChildRecord(
                1, followSynthBody, followingBuilder, List.of(TransactionSidecarRecord.newBuilder()));

        final var n = (short) 123;
        subject.customizeSuccessor(any -> false, childRecord -> {});
        subject.customizeSuccessor(
                any -> true, childRecord -> childRecord.recordBuilder().setNumChildRecords(n));

        verify(followingBuilder).setNumChildRecords(n);
    }

    @Test
    void incorporatesChildRecordsIfPresent() {
        final var mockFollowingRecord = mock(ExpirableTxnRecord.class);
        final var followingChildNows = nows + 1;
        given(mockFollowingRecord.getConsensusSecond()).willReturn(followingChildNows);
        final var mockPrecedingRecord = mock(ExpirableTxnRecord.class);
        final var precedingChildNows = nows - 1;
        given(mockPrecedingRecord.getConsensusSecond()).willReturn(precedingChildNows);
        given(mockPrecedingRecord.getEnumStatus()).willReturn(INVALID_ACCOUNT_ID);
        given(mockFollowingRecord.getEnumStatus()).willReturn(INVALID_CHUNK_NUMBER);

        final var expPrecedeId = txnIdA.toBuilder().setNonce(1).build();
        final var expFollowId = txnIdA.toBuilder().setNonce(2).build();

        final var expectedPrecedingChildId = new TxnId(aEntity, new RichInstant(nows, nanos), false, 1);
        final var expectedFollowingChildId = new TxnId(aEntity, new RichInstant(nows, nanos), false, 2);

        final var mockTopLevelRecord = mock(ExpirableTxnRecord.class);
        given(mockTopLevelRecord.getEnumStatus()).willReturn(SUCCESS);

        final var topLevelRecord = mock(ExpirableTxnRecord.Builder.class);
        given(topLevelRecord.getTxnId()).willReturn(TxnId.fromGrpc(txnIdA));
        final var followingBuilder = mock(ExpirableTxnRecord.Builder.class);
        given(followingBuilder.getTxnId()).willReturn(expectedFollowingChildId);
        given(mockFollowingRecord.getTxnId()).willReturn(expectedFollowingChildId);
        final var precedingBuilder = mock(ExpirableTxnRecord.Builder.class);
        given(precedingBuilder.getTxnId()).willReturn(expectedPrecedingChildId);
        given(mockPrecedingRecord.getTxnId()).willReturn(expectedPrecedingChildId);
        final var expectedFollowTime = topLevelNow.plusNanos(1);
        final var expectedPrecedingTime = topLevelNow.minusNanos(1);

        givenTopLevelContext();
        given(topLevelRecord.setNumChildRecords(anyShort())).willReturn(topLevelRecord);
        given(topLevelRecord.build()).willReturn(mockTopLevelRecord);
        given(followingBuilder.build()).willReturn(mockFollowingRecord);
        given(precedingBuilder.build()).willReturn(mockPrecedingRecord);
        given(consensusTimeTracker.isAllowableFollowingOffset(1)).willReturn(true);
        given(consensusTimeTracker.isAllowableFollowingOffset(2)).willReturn(true);
        given(consensusTimeTracker.isAllowablePrecedingOffset(1)).willReturn(true);

        given(txnCtx.recordSoFar()).willReturn(topLevelRecord);
        given(creator.saveExpiringRecord(effPayer, mockTopLevelRecord, nows, submittingMember))
                .willReturn(mockTopLevelRecord);
        given(creator.saveExpiringRecord(effPayer, mockFollowingRecord, followingChildNows, submittingMember))
                .willReturn(mockFollowingRecord);
        given(creator.saveExpiringRecord(effPayer, mockPrecedingRecord, precedingChildNows, submittingMember))
                .willReturn(mockPrecedingRecord);

        final var followSynthBody = aBuilderWith("FOLLOW");
        final var precedeSynthBody = aBuilderWith("PRECEDE");
        assertEquals(topLevelNow.plusNanos(1), subject.nextFollowingChildConsensusTime());
        final var bytecodeSidecar = SidecarUtils.createContractBytecodeSidecarFrom(
                asContract("0.0.1024"), "byte".getBytes(), "secondByte".getBytes());
        subject.trackFollowingChildRecord(1, followSynthBody, followingBuilder, List.of(bytecodeSidecar));
        assertEquals(topLevelNow.plusNanos(2), subject.nextFollowingChildConsensusTime());
        subject.trackPrecedingChildRecord(1, precedeSynthBody, precedingBuilder);

        subject.saveExpirableTransactionRecords();
        final var followingRsos = subject.getFollowingChildRecords();
        final var precedingRsos = subject.getPrecedingChildRecords();

        verify(topLevelRecord).excludeHbarChangesFrom(followingBuilder);
        verify(topLevelRecord).excludeHbarChangesFrom(precedingBuilder);
        verify(topLevelRecord).setNumChildRecords((short) 2);
        verify(followingBuilder).setConsensusTime(RichInstant.fromJava(expectedFollowTime));
        verify(followingBuilder).setParentConsensusTime(topLevelNow);
        verify(precedingBuilder).setConsensusTime(RichInstant.fromJava(expectedPrecedingTime));
        verify(precedingBuilder).setTxnId(expectedPrecedingChildId);
        verify(followingBuilder).setTxnId(expectedFollowingChildId);
        verify(consensusTimeTracker).setActualFollowingRecordsCount(1L);
        assertEquals(1, followingRsos.size());
        assertEquals(1, precedingRsos.size());

        final var precedeRso = precedingRsos.get(0);
        assertEquals(expectedPrecedingTime, precedeRso.getTimestamp());
        final var precedeSynth = precedeRso.getTransaction();
        final var expectedPrecedeSynth =
                synthFromBody(precedeSynthBody.setTransactionID(expPrecedeId).build());
        assertEquals(expectedPrecedeSynth, precedeSynth);

        final var followRso = followingRsos.get(0);
        assertEquals(expectedFollowTime, followRso.getTimestamp());
        final var followSynth = followRso.getTransaction();
        final var expectedFollowSynth =
                synthFromBody(followSynthBody.setTransactionID(expFollowId).build());
        assertEquals(expectedFollowSynth, followSynth);
        assertEquals(
                bytecodeSidecar.setConsensusTimestamp(Timestamp.newBuilder()
                        .setSeconds(expectedFollowTime.getEpochSecond())
                        .setNanos(expectedFollowTime.getNano())
                        .build()),
                followRso.getSidecars().get(0));

        verify(creator).saveExpiringRecord(effPayer, mockPrecedingRecord, precedingChildNows, submittingMember);
        verify(creator).saveExpiringRecord(effPayer, mockTopLevelRecord, nows, submittingMember);
        verify(creator).saveExpiringRecord(effPayer, mockFollowingRecord, followingChildNows, submittingMember);
        verifyBuilderUse(
                precedingBuilder,
                expectedPrecedingChildId,
                noThrowSha384HashOf(
                        expectedPrecedeSynth.getSignedTransactionBytes().toByteArray()));
        verifyBuilderUse(
                followingBuilder,
                expectedFollowingChildId,
                noThrowSha384HashOf(
                        expectedFollowSynth.getSignedTransactionBytes().toByteArray()));
        verify(recordCache).setPostConsensus(expPrecedeId, INVALID_ACCOUNT_ID, mockPrecedingRecord);
        verify(recordCache).setPostConsensus(expFollowId, INVALID_CHUNK_NUMBER, mockFollowingRecord);
    }

    @Test
    void systemTransactionNotAvailableUnlessTopLevelIsNonNull() {
        assertTrue(subject.nextSystemTransactionIdIsUnknown());
        assertThrows(IllegalStateException.class, () -> subject.computeNextSystemTransactionId());
    }

    @Test
    void systemTransactionIdsClearScheduledAndIncrementNonce() {
        final var scheduleCreatePayer =
                AccountID.newBuilder().setAccountNum(666_777_888L).build();
        final var scheduledTopLevelId = TransactionID.newBuilder()
                .setAccountID(scheduleCreatePayer)
                .setScheduled(true)
                .build();
        final var scheduledFinalRecordBuilder = ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(scheduledTopLevelId))
                .setHbarAdjustments(initialTransfers)
                .setMemo("This is different!")
                .setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build());
        final var scheduledFinalRecord = scheduledFinalRecordBuilder.build();
        givenTopLevelContextWith(scheduledTopLevelId);
        given(txnCtx.recordSoFar()).willReturn(scheduledFinalRecordBuilder);
        given(txnCtx.isPayerSigKnownActive()).willReturn(true);
        given(creator.saveExpiringRecord(scheduleCreatePayer, scheduledFinalRecord, nows, submittingMember))
                .willReturn(scheduledFinalRecord);
        final var mockTxn = Transaction.getDefaultInstance();
        given(accessor.getSignedTxnWrapper()).willReturn(mockTxn);

        // when:
        subject.saveExpirableTransactionRecords();

        // then:
        final var firstExpectedId = TxnId.fromGrpc(
                scheduledTopLevelId.toBuilder().clearScheduled().setNonce(1).build());
        final var secondExpectedId = TxnId.fromGrpc(
                scheduledTopLevelId.toBuilder().clearScheduled().setNonce(2).build());
        assertFalse(subject.nextSystemTransactionIdIsUnknown());
        assertEquals(firstExpectedId, subject.computeNextSystemTransactionId());
        assertEquals(secondExpectedId, subject.computeNextSystemTransactionId());
    }

    @Test
    void scheduledInvalidPayerSigStillGoesToNodeAccount() {
        final var scheduleCreatePayer =
                AccountID.newBuilder().setAccountNum(666_777_888L).build();
        final var scheduledTopLevelId = TransactionID.newBuilder()
                .setAccountID(scheduleCreatePayer)
                .setScheduled(true)
                .build();
        final var scheduledFinalRecordBuilder = ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(scheduledTopLevelId))
                .setHbarAdjustments(initialTransfers)
                .setMemo("This is different!")
                .setReceipt(TxnReceipt.newBuilder()
                        .setStatus(INVALID_PAYER_SIGNATURE.name())
                        .build());
        final var scheduledFinalRecord = scheduledFinalRecordBuilder.build();
        givenTopLevelContextWith(scheduledTopLevelId);
        final var nodeAccountId = AccountID.newBuilder().setAccountNum(8L).build();
        given(txnCtx.effectivePayer()).willReturn(nodeAccountId);
        given(txnCtx.recordSoFar()).willReturn(scheduledFinalRecordBuilder);
        given(creator.saveExpiringRecord(nodeAccountId, scheduledFinalRecord, nows, submittingMember))
                .willReturn(scheduledFinalRecord);
        final var mockTxn = Transaction.getDefaultInstance();
        given(accessor.getSignedTxnWrapper()).willReturn(mockTxn);

        // when:
        subject.saveExpirableTransactionRecords();

        // then:
        final var firstExpectedId = TxnId.fromGrpc(
                scheduledTopLevelId.toBuilder().clearScheduled().setNonce(1).build());
        final var secondExpectedId = TxnId.fromGrpc(
                scheduledTopLevelId.toBuilder().clearScheduled().setNonce(2).build());
        assertFalse(subject.nextSystemTransactionIdIsUnknown());
        assertEquals(firstExpectedId, subject.computeNextSystemTransactionId());
        assertEquals(secondExpectedId, subject.computeNextSystemTransactionId());
    }

    @Test
    void constructsTopLevelAsExpected() {
        givenTopLevelContext();
        given(txnCtx.recordSoFar()).willReturn(jFinalRecord);
        given(creator.saveExpiringRecord(effPayer, finalRecord.build(), nows, submittingMember))
                .willReturn(payerRecord);
        final var mockTxn = Transaction.getDefaultInstance();
        given(accessor.getSignedTxnWrapper()).willReturn(mockTxn);

        final var builtFinal = finalRecord.build();

        subject.saveExpirableTransactionRecords();

        verify(txnCtx).recordSoFar();
        verify(recordCache)
                .setPostConsensus(
                        txnIdA, ResponseCodeEnum.valueOf(builtFinal.getReceipt().getStatus()), payerRecord);
        verify(creator).saveExpiringRecord(effPayer, builtFinal, nows, submittingMember);
        verify(consensusTimeTracker).setActualFollowingRecordsCount(0L);
        // and:
        final var topLevelRso = subject.getTopLevelRecord();
        final var topLevel = topLevelRso.getExpirableTransactionRecord();
        assertEquals(builtFinal, topLevel);
        assertSame(mockTxn, topLevelRso.getTransaction());
        assertEquals(topLevelNow, topLevelRso.getTimestamp());
    }

    @Test
    void hasStreamableChildrenOnlyAfterSaving() {
        assertFalse(subject.hasPrecedingChildRecords());
        assertFalse(subject.hasFollowingChildRecords());
        given(consensusTimeTracker.isAllowableFollowingOffset(1)).willReturn(true);
        given(consensusTimeTracker.isAllowablePrecedingOffset(1)).willReturn(true);

        subject.trackFollowingChildRecord(
                1,
                TransactionBody.newBuilder(),
                ExpirableTxnRecord.newBuilder(),
                List.of(TransactionSidecarRecord.newBuilder()));
        subject.trackPrecedingChildRecord(1, TransactionBody.newBuilder(), ExpirableTxnRecord.newBuilder());

        assertFalse(subject.hasFollowingChildRecords());
        assertFalse(subject.hasPrecedingChildRecords());

        subject.clearHistory();

        assertFalse(subject.hasFollowingChildRecords());
        assertFalse(subject.hasPrecedingChildRecords());
    }

    @Test
    void throttlesOnlySuccessfulNonContractFollowingChildren() {
        final List<DeterministicThrottle.UsageSnapshot> pretendSnapshots =
                List.of(new DeterministicThrottle.UsageSnapshot(123, Instant.ofEpochSecond(456_789)));

        final var captor = ArgumentCaptor.forClass(TxnAccessor.class);
        given(consensusTimeTracker.isAllowableFollowingOffset(anyLong())).willReturn(true);
        given(handleThrottling.getUsageSnapshots()).willReturn(pretendSnapshots);

        doTrack(SUCCESS, body -> body.setContractCall(ContractCallTransactionBody.getDefaultInstance()));
        doTrack(SUCCESS, body -> body.setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance()));
        doTrack(REVERTED_SUCCESS, body -> body.setTokenMint(TokenMintTransactionBody.getDefaultInstance()));
        // Should be throttled
        doTrack(SUCCESS, body -> body.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance()));
        doTrack(INVALID_NFT_ID, body -> body.setTokenBurn(TokenBurnTransactionBody.getDefaultInstance()));
        // Should be throttled
        doTrack(SUCCESS, body -> body.setTokenMint(TokenMintTransactionBody.getDefaultInstance()));

        // No transactions will be throttled, shouldn't need to reset to snapshots
        assertTrue(subject.hasThrottleCapacityForChildTransactions());

        verify(handleThrottling, times(2)).shouldThrottleTxn(captor.capture());

        final var allThrottledAccessors = captor.getAllValues();
        assertEquals(
                List.of(HederaFunctionality.CryptoTransfer, HederaFunctionality.TokenMint),
                allThrottledAccessors.stream().map(TxnAccessor::getFunction).collect(toList()));
        verify(handleThrottling, never()).resetUsageThrottlesTo(pretendSnapshots);
    }

    @Test
    void revertsToPreOperationSnapshotsWhenThrottled() {
        final List<DeterministicThrottle.UsageSnapshot> pretendSnapshots =
                List.of(new DeterministicThrottle.UsageSnapshot(123, Instant.ofEpochSecond(456_789)));

        final var captor = ArgumentCaptor.forClass(TxnAccessor.class);
        given(consensusTimeTracker.isAllowableFollowingOffset(anyLong())).willReturn(true);
        given(handleThrottling.getUsageSnapshots()).willReturn(pretendSnapshots);
        given(handleThrottling.shouldThrottleTxn(any())).willReturn(false).willReturn(true);

        doTrack(SUCCESS, body -> body.setContractCall(ContractCallTransactionBody.getDefaultInstance()));
        doTrack(SUCCESS, body -> body.setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance()));
        doTrack(REVERTED_SUCCESS, body -> body.setTokenMint(TokenMintTransactionBody.getDefaultInstance()));
        // Should be throttled
        doTrack(SUCCESS, body -> body.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance()));
        doTrack(INVALID_NFT_ID, body -> body.setTokenBurn(TokenBurnTransactionBody.getDefaultInstance()));
        // Should be throttled
        doTrack(SUCCESS, body -> body.setTokenMint(TokenMintTransactionBody.getDefaultInstance()));

        // No transactions will be throttled, shouldn't need to reset to snapshots
        assertFalse(subject.hasThrottleCapacityForChildTransactions());

        verify(handleThrottling, times(2)).shouldThrottleTxn(captor.capture());

        final var allThrottledAccessors = captor.getAllValues();
        assertEquals(
                List.of(HederaFunctionality.CryptoTransfer, HederaFunctionality.TokenMint),
                allThrottledAccessors.stream().map(TxnAccessor::getFunction).collect(toList()));
        verify(handleThrottling).resetUsageThrottlesTo(pretendSnapshots);
    }

    private void doTrack(
            @NonNull final ResponseCodeEnum status, @NonNull final Consumer<TransactionBody.Builder> synthSpec) {
        final var body = TransactionBody.newBuilder();
        synthSpec.accept(body);
        final var matchingRecord = ExpirableTxnRecord.newBuilder()
                .setReceiptBuilder(TxnReceipt.newBuilder().setStatus(status.toString()));
        subject.trackFollowingChildRecord(1, body, matchingRecord, List.of(TransactionSidecarRecord.newBuilder()));
    }

    @Test
    void childRecordIdsIncrementThenReset() {
        assertEquals(1, subject.nextChildRecordSourceId());
        assertEquals(2, subject.nextChildRecordSourceId());

        subject.clearHistory();

        assertEquals(1, subject.nextChildRecordSourceId());
    }

    @Test
    void nextFollowingChildConsensusTimeErrorsOnTooManyChildTimes() {
        assertThrows(IllegalStateException.class, () -> subject.nextFollowingChildConsensusTime());

        verify(consensusTimeTracker).isAllowableFollowingOffset(1);
    }

    @Test
    void trackChildRecordsErrorsOnTooManyChildren() {
        assertFalse(subject.hasPrecedingChildRecords());
        assertFalse(subject.hasFollowingChildRecords());

        final var record = new InProgressChildRecord(0, TransactionBody.newBuilder(), mockRecordBuilder, List.of());
        subject.precedingChildRecords().add(record);
        subject.followingChildRecords().add(record);

        final var txn = TransactionBody.newBuilder();
        final var rec = ExpirableTxnRecord.newBuilder();
        final var sidecars = List.of(TransactionSidecarRecord.newBuilder());

        assertExhaustsResourceLimit(
                () -> subject.trackFollowingChildRecord(1, txn, rec, sidecars), MAX_CHILD_RECORDS_EXCEEDED);
        assertExhaustsResourceLimit(() -> subject.trackPrecedingChildRecord(1, txn, rec), MAX_CHILD_RECORDS_EXCEEDED);

        verify(consensusTimeTracker).isAllowableFollowingOffset(2);
        verify(consensusTimeTracker).isAllowablePrecedingOffset(2);
        verify(mockRecordBuilder, times(4)).revert();
    }

    private void givenTopLevelContext() {
        givenTopLevelContextWith(txnIdA);
    }

    private void givenTopLevelContextWith(final TransactionID txnId) {
        given(accessor.getTxnId()).willReturn(txnId);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(topLevelNow);
        given(txnCtx.submittingSwirldsMember()).willReturn(submittingMember);
        given(txnCtx.effectivePayer()).willReturn(effPayer);
    }

    private Transaction synthFromBody(final TransactionBody txnBody) {
        final var signedTxn = SignedTransaction.newBuilder()
                .setBodyBytes(txnBody.toByteString())
                .build();
        return Transaction.newBuilder()
                .setSignedTransactionBytes(signedTxn.toByteString())
                .build();
    }

    private TransactionBody.Builder aBuilderWith(final String memo) {
        return TransactionBody.newBuilder().setMemo(memo);
    }

    private void verifyBuilderUse(
            final ExpirableTxnRecord.Builder builder, final TxnId expectedId, final byte[] expectedHash) {
        verify(builder).setTxnId(expectedId);
        verify(builder).setTxnHash(expectedHash);
    }

    private ExpirableTxnRecord.Builder contractCreateAndCallResult() {
        final long gasUsed = 1_234;
        final byte[] result = "abcdefgh".getBytes();

        final byte[] evmAddress = Address.BLAKE2B_F_COMPRESSION.toArray();
        final List<EntityId> createdContractIds =
                List.of(new EntityId(1L, 2L, 3L), new EntityId(4L, 5L, 6L), new EntityId(7L, 8L, 9L));
        final List<ContractID> grpcCreatedContractIds =
                createdContractIds.stream().map(EntityId::toGrpcContractId).toList();

        final Address recipient = EntityNum.fromLong(3L).toEvmAddress();

        final var input = TransactionProcessingResult.successful(
                besuLogs,
                gasUsed,
                0,
                0,
                Bytes.wrap(result),
                recipient,
                Collections.emptyMap(),
                Collections.emptyList());
        input.setCreatedContracts(grpcCreatedContractIds);

        return ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(txnIdA))
                .setHbarAdjustments(initialTransfers)
                .setMemo("This is different!")
                .setContractCreateResult(EvmFnResult.fromCreate(input, evmAddress))
                .setContractCallResult(EvmFnResult.fromCall(input))
                .setReceipt(TxnReceipt.newBuilder().setStatus(SUCCESS.name()).build());
    }

    private static final byte[][] topics = new byte[][] {
        "alpha000000000000000000000000000".getBytes(),
        "bravo000000000000000000000000000".getBytes(),
        "charlie0000000000000000000000000".getBytes(),
    };

    private static final byte[][] otherTopics = new byte[][] {
        "alpha999999999999999999999999999".getBytes(),
        "bravo999999999999999999999999999".getBytes(),
        "charlie9999999999999999999999999".getBytes(),
    };

    private static final byte[][] blooms = new byte[][] {
        "tulip".getBytes(), "lily".getBytes(), "cynthia".getBytes(),
    };

    private static final byte[][] data = new byte[][] {
        "one".getBytes(), "two".getBytes(), "three".getBytes(),
    };
    private static final Log aLog = besuLog(123L, data[0], topics);
    private static final Log bLog = besuLog(456L, data[1], otherTopics);
    private static final List<Log> besuLogs = List.of(aLog, bLog);

    private static Log besuLog(final long num, byte[] data, byte[][] topics) {
        final var logger = EntityNum.fromLong(num);
        final var l = new Log(
                logger.toEvmAddress(),
                Bytes.wrap(data),
                Arrays.stream(topics)
                        .map(bytes -> LogTopic.of(Bytes.wrap(bytes)))
                        .toList());
        return l;
    }
}
