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

import static com.hedera.services.legacy.core.jproto.TxnReceipt.SUCCESS_LITERAL;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.charging.NarratedCharging;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.migration.RecordsStorageAdapter;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExpiringCreations implements EntityCreator {
    private final ExpiryManager expiries;
    private final NarratedCharging narratedCharging;
    private final GlobalDynamicProperties dynamicProperties;
    private final Supplier<RecordsStorageAdapter> payerRecords;

    @Inject
    public ExpiringCreations(
            final ExpiryManager expiries,
            final NarratedCharging narratedCharging,
            final GlobalDynamicProperties dynamicProperties,
            final Supplier<RecordsStorageAdapter> payerRecords) {
        this.payerRecords = payerRecords;
        this.expiries = expiries;
        this.narratedCharging = narratedCharging;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public void setLedger(final HederaLedger ledger) {
        narratedCharging.setLedger(ledger);
    }

    @Override
    public ExpirableTxnRecord saveExpiringRecord(
            final AccountID payer,
            final ExpirableTxnRecord expiringRecord,
            final long consensusTime,
            final long submittingMember) {
        final long expiry = consensusTime + dynamicProperties.cacheRecordsTtl();
        expiringRecord.setExpiry(expiry);
        expiringRecord.setSubmittingMember(submittingMember);

        final var key = EntityNum.fromAccountId(payer);
        addToState(key, expiringRecord);
        expiries.trackRecordInState(payer, expiringRecord.getExpiry());

        return expiringRecord;
    }

    @Override
    public ExpirableTxnRecord.Builder createSuccessfulSyntheticRecord(
            final List<FcAssessedCustomFee> assessedCustomFees,
            final SideEffectsTracker sideEffectsTracker,
            final String memo) {
        final var receiptBuilder = TxnReceipt.newBuilder().setStatus(SUCCESS_LITERAL);
        return createBaseRecord(memo, receiptBuilder, assessedCustomFees, sideEffectsTracker);
    }

    @Override
    public ExpirableTxnRecord.Builder createUnsuccessfulSyntheticRecord(
            final ResponseCodeEnum failureStatus) {
        final var receiptBuilder = TxnReceipt.newBuilder().setStatus(failureStatus.name());
        return ExpirableTxnRecord.newBuilder().setReceiptBuilder(receiptBuilder);
    }

    @Override
    public ExpirableTxnRecord.Builder createTopLevelRecord(
            final long fee,
            final byte[] hash,
            final TxnAccessor accessor,
            final Instant consensusTime,
            final TxnReceipt.Builder receiptBuilder,
            final List<FcAssessedCustomFee> customFeesCharged,
            final SideEffectsTracker sideEffectsTracker) {
        final var expiringRecord =
                createBaseRecord(
                        accessor.getMemo(), receiptBuilder, customFeesCharged, sideEffectsTracker);
        expiringRecord
                .setFee(fee)
                .setTxnHash(hash)
                .setTxnId(TxnId.fromGrpc(accessor.getTxnId()))
                .setConsensusTime(RichInstant.fromJava(consensusTime));

        if (accessor.isTriggeredTxn()) {
            expiringRecord.setScheduleRef(fromGrpcScheduleId(accessor.getScheduleRef()));
        }
        if (accessor.getFunction() == HederaFunctionality.EthereumTransaction) {
            expiringRecord.setEthereumHash(
                    accessor.getSpanMapAccessor().getEthTxDataMeta(accessor).getEthereumHash());
        }

        return expiringRecord;
    }

    private ExpirableTxnRecord.Builder createBaseRecord(
            final String memo,
            final TxnReceipt.Builder receiptBuilder,
            final List<FcAssessedCustomFee> customFeesCharged,
            final SideEffectsTracker sideEffectsTracker) {
        if (sideEffectsTracker.hasTrackedNewTokenId()) {
            receiptBuilder.setTokenId(
                    EntityId.fromGrpcTokenId(sideEffectsTracker.getTrackedNewTokenId()));
        }
        if (sideEffectsTracker.hasTrackedTokenSupply()) {
            receiptBuilder.setNewTotalSupply(sideEffectsTracker.getTrackedTokenSupply());
        }
        if (sideEffectsTracker.hasTrackedNftMints()) {
            final var serialNoList = sideEffectsTracker.getTrackedNftMints();
            final var rawSerials = new long[serialNoList.size()];
            Arrays.setAll(rawSerials, serialNoList::get);
            receiptBuilder.setSerialNumbers(rawSerials);
        }

        final var baseRecord =
                ExpirableTxnRecord.newBuilder()
                        .setReceiptBuilder(receiptBuilder)
                        .setMemo(memo)
                        .setHbarAdjustments(sideEffectsTracker.getNetTrackedHbarChanges())
                        .setStakingRewardsPaid(sideEffectsTracker.getStakingRewardsPaid())
                        .setAssessedCustomFees(customFeesCharged)
                        .setNewTokenAssociations(sideEffectsTracker.getTrackedAutoAssociations());

        if (sideEffectsTracker.hasTrackedAutoCreation()) {
            receiptBuilder.setAccountId(
                    EntityId.fromGrpcAccountId(
                            sideEffectsTracker.getTrackedAutoCreatedAccountId()));
            baseRecord.setAlias(sideEffectsTracker.getNewEntityAlias());
        }

        if (sideEffectsTracker.hasTrackedHollowAccountUpdate()) {
            receiptBuilder.setAccountId(
                    EntityId.fromGrpcAccountId(sideEffectsTracker.getTrackedHollowAccountId()));
        }

        final var tokenChanges = sideEffectsTracker.getNetTrackedTokenUnitAndOwnershipChanges();
        if (!tokenChanges.isEmpty()) {
            setTokensAndTokenAdjustments(baseRecord, tokenChanges);
        }
        if (sideEffectsTracker.hasTrackedContractCreation()) {
            final var newId =
                    EntityId.fromGrpcContractId(sideEffectsTracker.getTrackedNewContractId());
            receiptBuilder.setContractId(newId);
            final var createResult = new EvmFnResult();
            // A bit redundant, but set this for consistency with top-level records
            createResult.setContractId(newId);
            createResult.setEvmAddress(sideEffectsTracker.getNewEntityAlias().toByteArray());
            baseRecord.setContractCreateResult(createResult);
        }

        if (sideEffectsTracker.hasTrackedRandomData()) {
            if (sideEffectsTracker.getPseudorandomNumber() >= 0) {
                baseRecord.setPseudoRandomNumber(sideEffectsTracker.getPseudorandomNumber());
            } else {
                baseRecord.setPseudoRandomBytes(sideEffectsTracker.getPseudorandomBytes());
            }
        }

        return baseRecord;
    }

    @Override
    public ExpirableTxnRecord.Builder createInvalidFailureRecord(
            final TxnAccessor accessor, final Instant consensusTime) {
        final var txnId = accessor.getTxnId();

        return ExpirableTxnRecord.newBuilder()
                .setTxnId(TxnId.fromGrpc(txnId))
                .setReceipt(TxnReceipt.newBuilder().setStatus(FAIL_INVALID.name()).build())
                .setMemo(accessor.getMemo())
                .setTxnHash(accessor.getHash())
                .setConsensusTime(RichInstant.fromJava(consensusTime))
                .setScheduleRef(
                        accessor.isTriggeredTxn()
                                ? fromGrpcScheduleId(accessor.getScheduleRef())
                                : null);
    }

    private void setTokensAndTokenAdjustments(
            final ExpirableTxnRecord.Builder builder,
            final List<TokenTransferList> tokenTransferList) {
        final List<EntityId> tokens = new ArrayList<>();
        final List<CurrencyAdjustments> tokenAdjustments = new ArrayList<>();
        final List<NftAdjustments> nftTokenAdjustments = new ArrayList<>();
        for (final var tokenTransfer : tokenTransferList) {
            tokens.add(EntityId.fromGrpcTokenId(tokenTransfer.getToken()));
            tokenAdjustments.add(CurrencyAdjustments.fromGrpc(tokenTransfer.getTransfersList()));
            nftTokenAdjustments.add(NftAdjustments.fromGrpc(tokenTransfer.getNftTransfersList()));
        }
        builder.setTokens(tokens)
                .setTokenAdjustments(tokenAdjustments)
                .setNftTokenAdjustments(nftTokenAdjustments);
    }

    private void addToState(final EntityNum key, final ExpirableTxnRecord expirableTxnRecord) {
        payerRecords.get().addPayerRecord(key, expirableTxnRecord);
    }
}
