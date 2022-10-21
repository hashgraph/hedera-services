/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context;

import static com.hedera.services.context.BasicTransactionContext.EMPTY_KEY;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.asTopic;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.expiry.ExpiringEntity;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.SidecarUtils;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.SwirldsTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, LogCaptureExtension.class})
class BasicTransactionContextTest {
    private final TransactionID scheduledTxnId =
            TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")).build();
    private final long memberId = 3;
    private final long anotherMemberId = 4;
    private final Instant now = Instant.now();
    private final ExchangeRate rateNow =
            ExchangeRate.newBuilder()
                    .setHbarEquiv(1)
                    .setCentEquiv(100)
                    .setExpirationTime(TimestampSeconds.newBuilder())
                    .build();
    private final ExchangeRateSet ratesNow =
            ExchangeRateSet.newBuilder().setCurrentRate(rateNow).setNextRate(rateNow).build();
    private final AccountID payer = asAccount("0.0.2");
    private final AccountID anotherNodeAccount = asAccount("0.0.4");
    private final AccountID created = asAccount("0.0.2");
    private final AccountID another = asAccount("0.0.300");
    private final CurrencyAdjustments transfers =
            CurrencyAdjustments.fromChanges(
                    new long[] {-2L, 1L, 1L},
                    new long[] {
                        payer.getAccountNum(), created.getAccountNum(), another.getAccountNum()
                    });
    private final TokenID tokenCreated = asToken("3.0.2");
    private final ScheduleID scheduleCreated = asSchedule("0.0.10");
    private final TokenTransferList tokenTransfers =
            TokenTransferList.newBuilder()
                    .setToken(tokenCreated)
                    .addAllTransfers(
                            withAdjustments(payer, -2L, created, 1L, another, 1L)
                                    .getAccountAmountsList())
                    .build();
    private List<FcTokenAssociation> newTokenAssociations =
            List.of(new FcTokenAssociation(tokenCreated.getTokenNum(), payer.getAccountNum()));
    private final FileID fileCreated = asFile("2.0.1");
    private final ContractID contractCreated = asContract("0.1.2");
    private final TopicID topicCreated = asTopic("5.4.3");
    private final long txnValidStart = now.getEpochSecond() - 1_234L;
    private final AccountID nodeAccount = asAccount("0.0.3");
    private final String memo = "Hi!";
    private final byte[] hash = "fake hash".getBytes();
    private final TransactionID txnId =
            TransactionID.newBuilder()
                    .setTransactionValidStart(Timestamp.newBuilder().setSeconds(txnValidStart))
                    .setAccountID(payer)
                    .build();
    private ExpirableTxnRecord record;

    @Mock private HbarCentExchange exchange;
    @Mock private NodeInfo nodeInfo;
    @Mock private NarratedCharging narratedCharging;
    @Mock private SignedTxnAccessor accessor;
    @Mock private SignedTxnAccessor accessor2;
    @Mock private SwirldsTxnAccessor swirldsTxnAccessor;
    @Mock private TransactionBody txn;
    @Mock private ExpiringEntity expiringEntity;
    @Mock private JKey payerKey;
    @Mock private MerkleAccount payerAccount;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private EntityCreator creator;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private EntityIdSource ids;
    @Mock private EvmFnResult result;
    @Mock private EthTxData evmFnCallContext;

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private BasicTransactionContext subject;

    @BeforeEach
    void setup() {
        subject =
                new BasicTransactionContext(
                        narratedCharging,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        nodeInfo,
                        exchange,
                        creator,
                        sideEffectsTracker,
                        ids);

        subject.resetFor(accessor, now, memberId);

        verify(narratedCharging).resetForTxn(accessor, memberId);
    }

    @Test
    void throwIaeIfNoRecordedDeletionsOrDeletionNotRecorded() {
        assertEquals(0, subject.numDeletedAccountsAndContracts());
        assertThrows(IllegalArgumentException.class, () -> subject.getBeneficiaryOfDeleted(123L));
        subject.recordBeneficiaryOfDeleted(124L, 356L);
        subject.recordBeneficiaryOfDeleted(125L, 357L);
        assertEquals(2, subject.numDeletedAccountsAndContracts());
        assertThrows(IllegalArgumentException.class, () -> subject.getBeneficiaryOfDeleted(123L));
        assertEquals(356L, subject.getBeneficiaryOfDeleted(124L));
    }

    @Test
    void throwsIseIfNoPayerActive() {
        // expect:
        assertThrows(IllegalStateException.class, () -> subject.activePayer());
    }

    @Test
    void returnsPayerIfSigActive() {
        given(accessor.getPayer()).willReturn(payer);

        // when:
        subject.payerSigIsKnownActive();

        // expect:
        assertEquals(payer, subject.activePayer());
    }

    @Test
    void returnsEmptyKeyIfNoPayerActive() {
        // expect:
        assertEquals(EMPTY_KEY, subject.activePayerKey());
    }

    @Test
    void getsPayerKeyIfSigActive() {
        given(payerAccount.getAccountKey()).willReturn(payerKey);
        given(accounts.get(EntityNum.fromAccountId(payer))).willReturn(payerAccount);
        given(accessor.getPayer()).willReturn(payer);

        // when:
        subject.payerSigIsKnownActive();

        // then:
        assertEquals(payerKey, subject.activePayerKey());
    }

    @Test
    void getsExpectedNodeAccount() {
        given(nodeInfo.accountOf(memberId)).willReturn(nodeAccount);

        // expect:
        assertEquals(nodeAccount, subject.submittingNodeAccount());
    }

    @Test
    void failsHardForMissingMemberAccount() {
        given(nodeInfo.accountOf(memberId)).willThrow(IllegalArgumentException.class);

        // then:
        var ise = assertThrows(IllegalStateException.class, () -> subject.submittingNodeAccount());
        // and:
        assertThat(
                logCaptor.warnLogs(),
                contains(Matchers.startsWith("No available Hedera account for member 3!")));
        assertEquals("Member 3 must have a Hedera account!", ise.getMessage());
    }

    @Test
    void resetsRecordSoFar() {
        // given:
        subject.setRecordSoFar(mock(ExpirableTxnRecord.Builder.class));

        // when:
        subject.resetFor(accessor, now, anotherMemberId);

        // then:
        verify(subject.getRecordSoFar()).reset();
    }

    @Test
    void resetsEverythingElse() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(nodeInfo.accountOf(memberId)).willReturn(nodeAccount);
        given(nodeInfo.accountOf(anotherMemberId)).willReturn(anotherNodeAccount);
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);
        // and:
        subject.addFeeChargedToPayer(1_234L);
        subject.setCallResult(result);
        subject.setStatus(SUCCESS);
        subject.setTargetedContract(contractCreated);
        subject.payerSigIsKnownActive();
        subject.setAssessedCustomFees(Collections.emptyList());
        subject.recordBeneficiaryOfDeleted(1L, 2L);
        // and:
        assertEquals(memberId, subject.submittingSwirldsMember());
        assertEquals(nodeAccount, subject.submittingNodeAccount());

        // when:
        subject.resetFor(accessor, now, anotherMemberId);
        assertNull(subject.getAssessedCustomFees());
        assertThrows(IllegalArgumentException.class, () -> subject.getBeneficiaryOfDeleted(1L));
        assertEquals(0, subject.numDeletedAccountsAndContracts());
        // and:
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(
                ResponseCodeEnum.UNKNOWN,
                ResponseCodeEnum.valueOf(record.getReceipt().getStatus()));
        assertFalse(record.getReceipt().toGrpc().hasContractID());
        assertEquals(0, record.asGrpc().getTransactionFee());
        assertFalse(record.asGrpc().hasContractCallResult());
        assertFalse(subject.isPayerSigKnownActive());
        assertEquals(anotherNodeAccount, subject.submittingNodeAccount());
        assertEquals(anotherMemberId, subject.submittingSwirldsMember());
        assertEquals(newTokenAssociations.get(0), record.getNewTokenAssociations().get(0));
        // and:
        verify(narratedCharging).resetForTxn(accessor, memberId);
        verify(sideEffectsTracker, times(2)).reset();
        verify(ids, times(2)).resetProvisionalIds();
    }

    @Test
    void effectivePayerIsSubmittingNodeIfNotVerified() {
        given(nodeInfo.accountOf(memberId)).willReturn(nodeAccount);

        // expect:
        assertEquals(nodeAccount, subject.effectivePayer());
    }

    @Test
    void effectivePayerIsActiveIfVerified() {
        given(accessor.getPayer()).willReturn(payer);

        // when:
        subject.payerSigIsKnownActive();

        // expect:
        assertEquals(payer, subject.effectivePayer());
    }

    @Test
    void usesChargingToSetTransactionFee() {
        // setup:
        long std = 1_234L;
        long other = 4_321L;

        given(narratedCharging.totalFeesChargedToPayer()).willReturn(std);
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.addFeeChargedToPayer(other);

        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(std + other, record.asGrpc().getTransactionFee());
    }

    @Test
    void usesTokenTransfersToSetApropos() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(tokenTransfers, record.asGrpc().getTokenTransferLists(0));
    }

    @Test
    void configuresCallResult() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setCallResult(result);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        assertSame(result, record.getContractCallResult());
    }

    @Test
    void configuresEthereumHash() {
        var ethHash = new byte[] {2};
        var senderId = EntityId.fromIdentityCode(42);
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);
        given(evmFnCallContext.getEthereumHash()).willReturn(ethHash);

        // when:
        subject.setCallResult(result);
        subject.updateForEvmCall(evmFnCallContext, senderId);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        verify(result).updateForEvmCall(evmFnCallContext, senderId);
        assertArrayEquals(ethHash, record.getEthereumHash());
    }

    @Test
    void configuresCreateResult() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        setUpBuildingExpirableTxnRecord();
        subject.setCreateResult(result);
        record = subject.recordSoFar().build();

        assertSame(result, record.getContractCreateResult());
    }

    @Test
    void hasTransferList() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        setUpBuildingExpirableTxnRecord();
        // expect:
        final var transfers = withAdjustments(payer, -2L, created, 1L, another, 1L);
        assertEquals(transfers, subject.recordSoFar().build().asGrpc().getTransferList());
    }

    @Test
    void hasExpectedCopyFields() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getMemo()).willReturn(memo);
        given(accessor.getHash()).willReturn(hash);

        setUpBuildingExpirableTxnRecord();
        // when:
        ExpirableTxnRecord record = subject.recordSoFar().build();

        // expect:
        assertEquals(memo, record.getMemo());
        assertArrayEquals(hash, record.asGrpc().getTransactionHash().toByteArray());
        assertEquals(txnId, record.asGrpc().getTransactionID());
        assertEquals(RichInstant.fromJava(now), record.getConsensusTime());
    }

    @Test
    void hasExpectedPrimitives() {
        // expect:
        assertEquals(accessor, subject.accessor());
        assertEquals(now, subject.consensusTime());
        assertEquals(ResponseCodeEnum.UNKNOWN, subject.status());
    }

    @Test
    void hasExpectedStatus() {
        // when:
        subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);

        // then:
        assertEquals(ResponseCodeEnum.INVALID_PAYER_SIGNATURE, subject.status());
    }

    @Test
    void hasExpectedRecordStatus() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);
        given(txn.getMemo()).willReturn(memo);
        given(accessor.getHash()).willReturn(hash);

        // when:
        subject.setStatus(ResponseCodeEnum.INVALID_PAYER_SIGNATURE);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(
                ResponseCodeEnum.INVALID_PAYER_SIGNATURE,
                ResponseCodeEnum.valueOf(record.getReceipt().getStatus()));
    }

    @Test
    void getsExpectedReceiptForAccountCreation() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setCreated(created);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
        assertEquals(created, record.getReceipt().toGrpc().getAccountID());
    }

    @Test
    void getsExpectedReceiptForFileCreation() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setCreated(fileCreated);
        setUpBuildingExpirableTxnRecord();

        record = subject.recordSoFar().build();

        // then:
        assertEquals(ratesNow, TxnReceipt.convert(record.getReceipt()).getExchangeRate());
        assertEquals(fileCreated, record.getReceipt().toGrpc().getFileID());
    }

    @Test
    void getsExpectedReceiptForContractCreation() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setTargetedContract(contractCreated);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
        assertEquals(contractCreated, record.getReceipt().toGrpc().getContractID());
    }

    @Test
    void getsExpectedReceiptForTopicCreation() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setCreated(topicCreated);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
        assertEquals(topicCreated, record.getReceipt().toGrpc().getTopicID());
    }

    @Test
    void getsExpectedReceiptForSubmitMessage() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        var sequenceNumber = 1000L;
        var runningHash = new byte[11];

        // when:
        subject.setTopicRunningHash(runningHash, sequenceNumber);
        setUpBuildingExpirableTxnRecord();
        record = subject.recordSoFar().build();

        // then:
        assertEquals(ratesNow, record.getReceipt().toGrpc().getExchangeRate());
        assertArrayEquals(
                runningHash, record.getReceipt().toGrpc().getTopicRunningHash().toByteArray());
        assertEquals(sequenceNumber, record.getReceipt().getTopicSequenceNumber());
        assertEquals(
                MerkleTopic.RUNNING_HASH_VERSION,
                record.getReceipt().toGrpc().getTopicRunningHashVersion());
    }

    @Test
    void getsExpectedReceiptForSuccessfulScheduleOps() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // when:
        subject.setCreated(scheduleCreated);
        subject.setScheduledTxnId(scheduledTxnId);
        setUpBuildingExpirableTxnRecord();
        // and:
        record = subject.recordSoFar().build();

        // then:
        assertEquals(scheduleCreated, record.getReceipt().toGrpc().getScheduleID());
        assertEquals(scheduledTxnId, record.getReceipt().toGrpc().getScheduledTransactionID());
    }

    @Test
    void startsWithoutKnownValidPayerSig() {
        // expect:
        assertFalse(subject.isPayerSigKnownActive());
    }

    @Test
    void setsSigToKnownValid() {
        // given:
        subject.payerSigIsKnownActive();

        // expect:
        assertTrue(subject.isPayerSigKnownActive());
    }

    @Test
    void triggersTxn() {
        // when:
        subject.trigger(accessor);

        // then:
        assertEquals(subject.triggeredTxn(), accessor);
    }

    @Test
    void getsExpectedRecordForTriggeredTxn() {
        given(exchange.fcActiveRates()).willReturn(ExchangeRates.fromGrpc(ratesNow));
        given(accessor.getTxnId()).willReturn(txnId);
        given(accessor.getTxn()).willReturn(txn);

        // and:
        given(accessor.getScheduleRef()).willReturn(scheduleCreated);
        given(accessor.isTriggeredTxn()).willReturn(true);
        setUpBuildingExpirableTxnRecord();

        // when:
        record = subject.recordSoFar().build();

        // then:
        assertEquals(fromGrpcScheduleId(scheduleCreated), record.getScheduleRef());
    }

    @Test
    void addsExpiringEntities() {
        // given:
        var expected = Collections.singletonList(expiringEntity);

        // when:
        subject.addExpiringEntities(expected);

        // then:
        assertEquals(subject.expiringEntities(), expected);
    }

    @Test
    void throwsIfAccessorIsAlreadyTriggered() {
        given(accessor.isTriggeredTxn()).willReturn(true);
        assertThrows(IllegalStateException.class, () -> subject.trigger(accessor));
    }

    @Test
    void throwsOnMoreThanOneTrigger() {
        subject.trigger(accessor);
        subject.trigger(accessor);
        assertThrows(IllegalStateException.class, () -> subject.trigger(accessor2));
    }

    @Test
    void hasContractResultWorksForCreateWithResult() {
        subject.setCreateResult(result);
        assertTrue(subject.hasContractResult());
    }

    @Test
    void hasContractResultWorksWithoutResult() {
        assertFalse(subject.hasContractResult());
    }

    @Test
    void getGasUsedForContractTXWorksForCreate() {
        final var gasUsed = 123456789L;
        given(result.getGasUsed()).willReturn(gasUsed);
        subject.setCreateResult(result);
        assertEquals(gasUsed, subject.getGasUsedForContractTxn());
    }

    @Test
    void getGasUsedForContractTXWorksForCall() {
        final var gasUsed = 123456789L;
        given(result.getGasUsed()).willReturn(gasUsed);
        subject.setCallResult(result);
        assertEquals(gasUsed, subject.getGasUsedForContractTxn());
    }

    @Test
    void throwsIfNotSwirldsTxnAccessor() {
        assertThrows(IllegalStateException.class, () -> subject.swirldsTxnAccessor());

        subject =
                new BasicTransactionContext(
                        narratedCharging,
                        () -> AccountStorageAdapter.fromInMemory(accounts),
                        nodeInfo,
                        exchange,
                        creator,
                        sideEffectsTracker,
                        ids);

        subject.resetFor(swirldsTxnAccessor, now, memberId);
        verify(narratedCharging).resetForTxn(swirldsTxnAccessor, memberId);

        assertEquals(swirldsTxnAccessor, subject.swirldsTxnAccessor());
    }

    @Test
    void sidecarsArePopulatedAsExpected() {
        final var sidecar =
                SidecarUtils.createContractBytecodeSidecarFrom(
                        asContract("0.0.5"), "runtimeCode".getBytes());
        final var sidecar2 =
                SidecarUtils.createContractBytecodeSidecarFrom(
                        asContract("0.0.7"), "runtimeCode2".getBytes());

        subject.addSidecarRecord(sidecar);
        assertEquals(1, subject.sidecars().size());
        subject.addSidecarRecord(sidecar2);
        assertEquals(2, subject.sidecars().size());

        final var sidecars = subject.sidecars();
        assertEquals(sidecar, sidecars.get(0));
        assertEquals(sidecar2, sidecars.get(1));
    }

    private ExpirableTxnRecord.Builder buildExpectedRecord(
            long otherNonThresholdFees,
            byte[] hash,
            TxnAccessor accessor,
            Instant consensusTime,
            TxnReceipt receipt) {
        long amount = narratedCharging.totalFeesChargedToPayer() + otherNonThresholdFees;
        List<TokenTransferList> tokenTransferList = List.of(tokenTransfers);

        var builder =
                ExpirableTxnRecord.newBuilder()
                        .setReceipt(receipt)
                        .setTxnHash(hash)
                        .setTxnId(TxnId.fromGrpc(accessor.getTxnId()))
                        .setConsensusTime(RichInstant.fromJava(consensusTime))
                        .setMemo(accessor.getTxn().getMemo())
                        .setFee(amount)
                        .setHbarAdjustments(transfers)
                        .setScheduleRef(
                                accessor.isTriggeredTxn()
                                        ? fromGrpcScheduleId(accessor.getScheduleRef())
                                        : null)
                        .setNewTokenAssociations(newTokenAssociations);

        List<EntityId> tokens = new ArrayList<>();
        List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
        for (TokenTransferList tokenTransfers : tokenTransferList) {
            tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
            tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
        }

        builder.setTokens(tokens).setTokenAdjustments(tokenAdjustments);
        return builder;
    }

    private ExpirableTxnRecord.Builder setUpBuildingExpirableTxnRecord() {
        var expirableRecordBuilder =
                buildExpectedRecord(
                        subject.getNonThresholdFeeChargedToPayer(),
                        accessor.getHash(),
                        accessor,
                        now,
                        subject.receiptSoFar().build());
        when(creator.createTopLevelRecord(anyLong(), any(), any(), any(), any(), any(), any()))
                .thenReturn(expirableRecordBuilder);
        return expirableRecordBuilder;
    }
}
