/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import static com.hedera.services.state.submerkle.EntityId.fromGrpcScheduleId;
import static com.hedera.services.state.submerkle.ExpirableTxnRecord.NO_TOKENS;
import static java.util.stream.Collectors.toList;

import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.utils.SerdeUtils;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.ArrayList;
import java.util.List;

public class ExpirableTxnRecordTestHelper {
    public static ExpirableTxnRecord fromGprc(TransactionRecord record) {
        List<EntityId> tokens = NO_TOKENS;
        List<CurrencyAdjustments> tokenAdjustments = null;
        List<NftAdjustments> nftTokenAdjustments = null;
        int n = record.getTokenTransferListsCount();

        if (n > 0) {
            tokens = new ArrayList<>();
            tokenAdjustments = new ArrayList<>();
            nftTokenAdjustments = new ArrayList<>();
            for (TokenTransferList tokenTransfers : record.getTokenTransferListsList()) {
                tokens.add(EntityId.fromGrpcTokenId(tokenTransfers.getToken()));
                tokenAdjustments.add(
                        CurrencyAdjustments.fromGrpc(tokenTransfers.getTransfersList()));
                nftTokenAdjustments.add(
                        NftAdjustments.fromGrpc(tokenTransfers.getNftTransfersList()));
            }
        }

        return createExpiryTxnRecordFrom(record, tokens, tokenAdjustments, nftTokenAdjustments);
    }

    private static ExpirableTxnRecord createExpiryTxnRecordFrom(
            final TransactionRecord record,
            final List<EntityId> tokens,
            final List<CurrencyAdjustments> tokenAdjustments,
            final List<NftAdjustments> nftTokenAdjustments) {

        final var fcAssessedFees =
                record.getAssessedCustomFeesCount() > 0
                        ? record.getAssessedCustomFeesList().stream()
                                .map(FcAssessedCustomFee::fromGrpc)
                                .collect(toList())
                        : null;
        final var newTokenAssociations =
                record.getAutomaticTokenAssociationsList().stream()
                        .map(FcTokenAssociation::fromGrpc)
                        .collect(toList());
        final var builder =
                ExpirableTxnRecord.newBuilder()
                        .setReceipt(fromGrpc(record.getReceipt()))
                        .setTxnHash(record.getTransactionHash().toByteArray())
                        .setTxnId(TxnId.fromGrpc(record.getTransactionID()))
                        .setConsensusTime(RichInstant.fromGrpc(record.getConsensusTimestamp()))
                        .setMemo(record.getMemo())
                        .setFee(record.getTransactionFee())
                        .setHbarAdjustments(
                                record.hasTransferList()
                                        ? CurrencyAdjustments.fromGrpc(
                                                record.getTransferList().getAccountAmountsList())
                                        : null)
                        .setStakingRewardsPaid(
                                CurrencyAdjustments.fromGrpc(record.getPaidStakingRewardsList()))
                        .setContractCallResult(
                                record.hasContractCallResult()
                                        ? SerdeUtils.fromGrpc(record.getContractCallResult())
                                        : null)
                        .setContractCreateResult(
                                record.hasContractCreateResult()
                                        ? SerdeUtils.fromGrpc(record.getContractCreateResult())
                                        : null)
                        .setTokens(tokens)
                        .setTokenAdjustments(tokenAdjustments)
                        .setNftTokenAdjustments(nftTokenAdjustments)
                        .setScheduleRef(
                                record.hasScheduleRef()
                                        ? fromGrpcScheduleId(record.getScheduleRef())
                                        : null)
                        .setAssessedCustomFees(fcAssessedFees)
                        .setNewTokenAssociations(newTokenAssociations)
                        .setAlias(record.getAlias())
                        .setEthereumHash(record.getEthereumHash().toByteArray());
        if (!record.getPrngBytes().isEmpty()) {
            builder.setPseudoRandomBytes(record.getPrngBytes().toByteArray());
        }
        if (record.getPrngNumber() > 0) {
            builder.setPseudoRandomNumber(record.getPrngNumber());
        }
        if (record.hasParentConsensusTimestamp()) {
            builder.setParentConsensusTime(
                    MiscUtils.timestampToInstant(record.getParentConsensusTimestamp()));
        }
        return builder.build();
    }

    /* ---  Helpers --- */
    public static TxnReceipt fromGrpc(TransactionReceipt grpc) {
        final var effRates =
                grpc.hasExchangeRate() ? ExchangeRates.fromGrpc(grpc.getExchangeRate()) : null;
        String status = grpc.getStatus() != null ? grpc.getStatus().name() : null;
        EntityId accountId =
                grpc.hasAccountID() ? EntityId.fromGrpcAccountId(grpc.getAccountID()) : null;
        EntityId jFileID = grpc.hasFileID() ? EntityId.fromGrpcFileId(grpc.getFileID()) : null;
        EntityId jContractID =
                grpc.hasContractID() ? EntityId.fromGrpcContractId(grpc.getContractID()) : null;
        EntityId topicId = grpc.hasTopicID() ? EntityId.fromGrpcTopicId(grpc.getTopicID()) : null;
        EntityId tokenId = grpc.hasTokenID() ? EntityId.fromGrpcTokenId(grpc.getTokenID()) : null;
        EntityId scheduleId =
                grpc.hasScheduleID() ? fromGrpcScheduleId(grpc.getScheduleID()) : null;
        long runningHashVersion =
                Math.max(
                        TxnReceipt.MISSING_RUNNING_HASH_VERSION, grpc.getTopicRunningHashVersion());
        long newTotalSupply = grpc.getNewTotalSupply();
        long[] serialNumbers = grpc.getSerialNumbersList().stream().mapToLong(l -> l).toArray();
        TxnId scheduledTxnId =
                grpc.hasScheduledTransactionID()
                        ? TxnId.fromGrpc(grpc.getScheduledTransactionID())
                        : null;
        return TxnReceipt.newBuilder()
                .setStatus(status)
                .setAccountId(accountId)
                .setFileId(jFileID)
                .setContractId(jContractID)
                .setTokenId(tokenId)
                .setScheduleId(scheduleId)
                .setExchangeRates(effRates)
                .setTopicId(topicId)
                .setTopicSequenceNumber(grpc.getTopicSequenceNumber())
                .setTopicRunningHash(grpc.getTopicRunningHash().toByteArray())
                .setRunningHashVersion(runningHashVersion)
                .setNewTotalSupply(newTotalSupply)
                .setScheduledTxnId(scheduledTxnId)
                .setSerialNumbers(serialNumbers)
                .build();
    }
}
