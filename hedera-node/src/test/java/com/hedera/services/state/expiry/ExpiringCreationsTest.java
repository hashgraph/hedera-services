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
package com.hedera.services.state.expiry;

import static com.hedera.services.state.EntityCreator.EMPTY_MEMO;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.merkle.map.MerkleMap;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiringCreationsTest {
    private static final int cacheTtl = 180;
    private static final long now = 1_234_567L;
    private static final long submittingMember = 1L;
    private static final long expectedExpiry = now + cacheTtl;
    private static final long totalFee = 666_666L;

    private static final long newTokenSupply = 1_234_567L;
    private static final TokenID newTokenId = IdUtils.asToken("0.0.666");
    private static final AccountID effPayer = IdUtils.asAccount("0.0.75231");
    private static final ExpirableTxnRecord record = TxnUtils.recordOne();
    private static ExpirableTxnRecord expectedRecord;

    @Mock private ExpiryManager expiries;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private NarratedCharging narratedCharging;
    @Mock private HederaLedger ledger;
    @Mock private TxnAccessor accessor;
    @Mock private SideEffectsTracker sideEffectsTracker;
    @Mock private ExpandHandleSpanMapAccessor expandHandleSpanMapAccessor;
    @Mock private EthTxData ethTxData;

    private static final AccountID payer = asAccount("0.0.2");
    private static final AccountID created = asAccount("1.0.2");
    private static final AccountID another = asAccount("1.0.300");
    private static final CurrencyAdjustments transfers =
            CurrencyAdjustments.fromGrpc(
                    withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList());
    private static final TokenID tokenCreated = asToken("3.0.2");
    private static final List<AccountAmount> adjustments =
            withAdjustments(payer, -2L, created, 1L, another, 1L).getAccountAmountsList();
    private static final TokenTransferList tokenTransfers =
            TokenTransferList.newBuilder()
                    .setToken(tokenCreated)
                    .addAllTransfers(adjustments)
                    .build();
    private static final List<TokenTransferList> netTokenChanges = List.of(tokenTransfers);

    private static final String memo = "TEST_MEMO";
    private static final String hashString = "TEST";
    private static final long scheduleNum = 100L;
    private static final String account = "0.0.10001";
    private static final Instant timestamp = Instant.now();
    private static final byte[] hash = hashString.getBytes(StandardCharsets.UTF_8);
    private static final TransactionID grpcTxnId =
            TransactionID.newBuilder().setAccountID(asAccount(account)).build();
    private static final ScheduleID scheduleRef =
            ScheduleID.newBuilder().setScheduleNum(scheduleNum).build();

    private ExpiringCreations subject;

    private static final TxnReceipt.Builder receiptBuilder = receiptBuilderWith(SUCCESS);

    private static final EntityId customFeeToken = new EntityId(0, 0, 123);
    private static final EntityId customFeeCollector = new EntityId(0, 0, 124);
    private static final List<FcAssessedCustomFee> customFeesCharged =
            List.of(
                    new FcAssessedCustomFee(
                            customFeeCollector, customFeeToken, 123L, new long[] {123L}));
    private static final List<FcTokenAssociation> newTokenAssociations =
            List.of(new FcTokenAssociation(customFeeToken.num(), customFeeCollector.num()));

    @BeforeEach
    void setup() {
        subject =
                new ExpiringCreations(
                        expiries, narratedCharging, dynamicProperties, () -> accounts);
        subject.setLedger(ledger);

        expectedRecord = record;
        expectedRecord.setExpiry(expectedExpiry);
        expectedRecord.setSubmittingMember(submittingMember);

        verify(narratedCharging).setLedger(ledger);
    }

    @Test
    void createsSuccessfulSyntheticRecordAsExpectedWithNewContractAddress() {
        final var addr = Address.BLAKE2B_F_COMPRESSION;
        final var id = IdUtils.asContract("0.0.12324");
        setupTracker();
        given(sideEffectsTracker.hasTrackedContractCreation()).willReturn(true);
        given(sideEffectsTracker.getTrackedNewContractId()).willReturn(id);
        given(sideEffectsTracker.getNewEntityAlias())
                .willReturn(ByteString.copyFrom(addr.toArrayUnsafe()));

        final var record =
                subject.createSuccessfulSyntheticRecord(
                        Collections.emptyList(), sideEffectsTracker, EMPTY_MEMO);

        final var createdId = EntityId.fromGrpcContractId(id);
        assertEquals(SUCCESS.toString(), record.getReceiptBuilder().getStatus());
        assertEquals(createdId, record.getReceiptBuilder().getContractId());
        final var createFnResult = record.getContractCreateResult();
        assertEquals(createdId, createFnResult.getContractId());
        assertArrayEquals(addr.toArrayUnsafe(), createFnResult.getEvmAddress());
    }

    @Test
    void createsSuccessfulSyntheticRecordAsExpected() {
        setupTracker();
        final var tokensExpected = List.of(EntityId.fromGrpcTokenId(tokenCreated));
        final var tokenAdjustmentsExpected = List.of(CurrencyAdjustments.fromGrpc(adjustments));

        final var record =
                subject.createSuccessfulSyntheticRecord(
                        customFeesCharged, sideEffectsTracker, EMPTY_MEMO);

        assertEquals(SUCCESS.toString(), record.getReceiptBuilder().getStatus());
        assertEquals(tokensExpected, record.getTokens());
        assertEquals(tokenAdjustmentsExpected, record.getTokenAdjustments());
        assertEquals(customFeesCharged, record.getAssessedCustomFees());
    }

    @Test
    void createsFailedSyntheticRecordAsExpected() {
        final var record = subject.createUnsuccessfulSyntheticRecord(INSUFFICIENT_ACCOUNT_BALANCE);
        assertEquals(
                INSUFFICIENT_ACCOUNT_BALANCE.toString(), record.getReceiptBuilder().getStatus());
    }

    @Test
    void addsToPayerRecordsAndTracks() {
        // setup:
        final var key = EntityNum.fromAccountId(effPayer);
        final var payerAccount = new MerkleAccount();
        given(accounts.getForModify(key)).willReturn(payerAccount);
        given(dynamicProperties.cacheRecordsTtl()).willReturn(cacheTtl);

        final var actual = subject.saveExpiringRecord(effPayer, record, now, submittingMember);

        assertEquals(expectedRecord, actual);
        verify(expiries).trackRecordInState(effPayer, expectedExpiry);
        assertEquals(expectedRecord, payerAccount.records().peek());
    }

    @Test
    void validateBuildFailedExpiringRecord() {
        setUpForExpiringRecordBuilder();
        given(accessor.getHash()).willReturn(hash);

        final var builder = subject.createInvalidFailureRecord(accessor, timestamp);
        final var actualRecord = builder.build();

        validateCommonFields(actualRecord, receiptBuilderWith(FAIL_INVALID));
    }

    @Test
    void includesMintedSerialNos() {
        final var mockMints = List.of(1L, 2L);
        setupTrackerNoUnitOrOwnershipChanges();
        setUpForExpiringRecordBuilder();

        given(sideEffectsTracker.hasTrackedNftMints()).willReturn(true);
        given(sideEffectsTracker.getTrackedNftMints()).willReturn(mockMints);

        final var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertArrayEquals(
                mockMints.stream().mapToLong(l -> l).toArray(),
                created.getReceipt().getSerialNumbers());
    }

    @Test
    void includesAutoCreatedAliases() {
        final var mockAlias = ByteString.copyFromUtf8("make-believe");
        setupTrackerNoUnitOrOwnershipChanges();
        setUpForExpiringRecordBuilder();

        given(sideEffectsTracker.hasTrackedAutoCreation()).willReturn(true);
        given(sideEffectsTracker.getTrackedAutoCreatedAccountId()).willReturn(effPayer);
        given(sideEffectsTracker.getNewEntityAlias()).willReturn(mockAlias);

        final var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertEquals(effPayer, created.getReceipt().getAccountId().toGrpcAccountId());
        assertEquals(mockAlias, created.getAlias());
    }

    @Test
    void createsExpectedRecordForNonTriggeredTxnWithNoTokenChanges() {
        setupTrackerNoUnitOrOwnershipChanges();
        setUpForExpiringRecordBuilder();

        final var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertEquals(totalFee, created.getFee());
        assertSame(hash, created.getTxnHash());
        assertEquals(memo, created.getMemo());
        assertEquals(receiptBuilder.build(), created.getReceipt());
        assertEquals(timestamp, created.getConsensusTime().toJava());
        assertEquals(scheduleRef, created.getScheduleRef().toGrpcScheduleId());
        assertNull(created.getTokens());
        assertNull(created.getTokenAdjustments());
        assertNull(created.getNftTokenAdjustments());
    }

    @Test
    void createsExpectedRecordForNonTriggeredTxnWithTokenChangesAndCustomFees() {
        setupTracker();
        setupAccessorForNonTriggeredTxn();
        final var tokensExpected = List.of(EntityId.fromGrpcTokenId(tokenCreated));
        final var tokenAdjustmentsExpected = List.of(CurrencyAdjustments.fromGrpc(adjustments));

        given(sideEffectsTracker.hasTrackedNewTokenId()).willReturn(true);
        given(sideEffectsTracker.getTrackedNewTokenId()).willReturn(newTokenId);
        given(sideEffectsTracker.hasTrackedTokenSupply()).willReturn(true);
        given(sideEffectsTracker.getTrackedTokenSupply()).willReturn(newTokenSupply);

        final var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        final var actualReceipt = created.getReceipt();
        assertEquals(EntityId.fromGrpcTokenId(newTokenId), actualReceipt.getTokenId());
        assertEquals(newTokenSupply, actualReceipt.getNewTotalSupply());

        assertEquals(tokensExpected, created.getTokens());
        assertEquals(tokenAdjustmentsExpected, created.getTokenAdjustments());
        assertEquals(customFeesCharged, created.getCustomFeesCharged());
        assertEquals(totalFee, created.getFee());
        assertSame(hash, created.getTxnHash());
        assertEquals(memo, created.getMemo());
        assertEquals(timestamp, created.getConsensusTime().toJava());
        assertNull(created.getScheduleRef());
    }

    @Test
    void includesEthereumHash() {
        final var mockHash = ByteString.copyFromUtf8("corn-beef").toByteArray();
        setUpForExpiringRecordBuilder();

        given(accessor.getFunction()).willReturn(EthereumTransaction);
        given(accessor.getSpanMapAccessor()).willReturn(expandHandleSpanMapAccessor);
        given(expandHandleSpanMapAccessor.getEthTxDataMeta(accessor)).willReturn(ethTxData);
        given(ethTxData.getEthereumHash()).willReturn(mockHash);

        final var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertArrayEquals(mockHash, created.getEthereumHash());
    }

    @Test
    void includesPseudoRandomData() {
        final var mockString = ByteString.copyFromUtf8("corn-beef");
        setUpForExpiringRecordBuilder();

        // case 1
        given(accessor.getFunction()).willReturn(UtilPrng);
        given(sideEffectsTracker.getPseudorandomNumber()).willReturn(10);
        given(sideEffectsTracker.hasTrackedRandomData()).willReturn(true);

        var created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertEquals(10, created.getPseudoRandomNumber());
        assertEquals(0, created.getPseudoRandomBytes().length);

        // case 2
        given(sideEffectsTracker.getPseudorandomNumber()).willReturn(-1);
        given(sideEffectsTracker.getPseudorandomBytes()).willReturn(mockString.toByteArray());
        given(sideEffectsTracker.hasTrackedRandomData()).willReturn(true);

        created =
                subject.createTopLevelRecord(
                                totalFee,
                                hash,
                                accessor,
                                timestamp,
                                receiptBuilder,
                                customFeesCharged,
                                sideEffectsTracker)
                        .build();

        assertEquals(-1, created.getPseudoRandomNumber());
        assertArrayEquals(mockString.toByteArray(), created.getPseudoRandomBytes());
    }

    private void setupTracker() {
        given(sideEffectsTracker.getNetTrackedHbarChanges()).willReturn(transfers);
        given(sideEffectsTracker.getTrackedAutoAssociations()).willReturn(newTokenAssociations);
        given(sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges())
                .willReturn(netTokenChanges);
    }

    private void setupTrackerNoUnitOrOwnershipChanges() {
        given(sideEffectsTracker.getNetTrackedHbarChanges()).willReturn(transfers);
        given(sideEffectsTracker.getTrackedAutoAssociations()).willReturn(newTokenAssociations);
        given(sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges()).willReturn(List.of());
    }

    private void validateCommonFields(
            final ExpirableTxnRecord actualRecord, final TxnReceipt.Builder receipt) {
        assertEquals(grpcTxnId, actualRecord.getTxnId().toGrpc());
        assertEquals(receipt.build(), actualRecord.getReceipt());
        assertEquals(memo, actualRecord.getMemo());
        assertArrayEquals(hash, actualRecord.getTxnHash());
        assertEquals(timestamp, actualRecord.getConsensusTime().toJava());
        assertEquals(scheduleRef, actualRecord.getScheduleRef().toGrpcScheduleId());
    }

    private static TxnReceipt.Builder receiptBuilderWith(final ResponseCodeEnum code) {
        return TxnReceipt.newBuilder().setStatus(code.name());
    }

    private void setUpForExpiringRecordBuilder() {
        given(accessor.getTxnId()).willReturn(grpcTxnId);
        given(accessor.getMemo()).willReturn(memo);
        given(accessor.isTriggeredTxn()).willReturn(true);
        given(accessor.getScheduleRef()).willReturn(scheduleRef);
    }

    private void setupAccessorForNonTriggeredTxn() {
        given(accessor.getTxnId()).willReturn(grpcTxnId);
        given(accessor.getMemo()).willReturn(memo);
    }
}
