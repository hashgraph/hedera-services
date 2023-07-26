/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.record;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.impl.records.TokenCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom builder for create a {@link SingleTransactionRecord}.
 *
 * <p>The protobuf definition for the record files is defined such that a single protobuf object intermixes the
 * possible fields for all different types of transaction in a single object definition. We wanted to provide something
 * nicer and more modular for service authors, so we broke out each logical grouping of state in the record file into
 * different interfaces, such as {@link ConsensusSubmitMessageRecordBuilder} and {@link CreateFileRecordBuilder}, and
 * so forth. Services interact with these builder interfaces, and are thus isolated from details that don't pertain to
 * their service type.
 *
 * <p>This class is an ugly superset of all fields for all transaction types. It is masked down to a sensible subset by
 * the interfaces for specific transaction types.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class SingleTransactionRecordBuilderImpl
        implements SingleTransactionRecordBuilder,
                ConsensusCreateTopicRecordBuilder,
                ConsensusSubmitMessageRecordBuilder,
                CreateFileRecordBuilder,
                CryptoCreateRecordBuilder,
                CryptoTransferRecordBuilder,
                PrngRecordBuilder,
                TokenMintRecordBuilder,
                TokenCreateRecordBuilder {
    // base transaction data
    private Transaction transaction;
    private Bytes transactionBytes = Bytes.EMPTY;
    // fields needed for TransactionRecord
    private final Instant consensusNow;
    private final Instant parentConsensus;
    private List<TokenTransferList> tokenTransferLists = emptyList();
    private List<AssessedCustomFee> assessedCustomFees = emptyList();
    private List<TokenAssociation> automaticTokenAssociations = emptyList();
    private List<AccountAmount> paidStakingRewards = emptyList();
    private final TransactionRecord.Builder transactionRecordBuilder = TransactionRecord.newBuilder();

    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private List<Long> serialNumbers = emptyList();
    private final TransactionReceipt.Builder transactionReceiptBuilder = TransactionReceipt.newBuilder();
    // Sidecar data, booleans are the migration flag
    private List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges = new ArrayList<>();
    private List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new ArrayList<>();
    private List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new ArrayList<>();

    public SingleTransactionRecordBuilderImpl(@NonNull final Instant consensusNow) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.parentConsensus = null;
    }

    public SingleTransactionRecordBuilderImpl(
            @NonNull final Instant consensusNow, @NonNull final Instant parentConsensus) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.parentConsensus = requireNonNull(parentConsensus, "parentConsensusTimestamp must not be null");
    }

    public SingleTransactionRecord build() {
        final var transactionReceipt =
                this.transactionReceiptBuilder.serialNumbers(this.serialNumbers).build();

        final Bytes transactionHash;
        try {
            final MessageDigest digest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
            transactionHash = Bytes.wrap(digest.digest(transactionBytes.toByteArray()));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        final Timestamp consensusTimestamp = HapiUtils.asTimestamp(consensusNow);
        final Timestamp parentConsensusTimestamp =
                parentConsensus != null ? HapiUtils.asTimestamp(parentConsensus) : null;

        final var transactionRecord = this.transactionRecordBuilder
                .receipt(transactionReceipt)
                .transactionHash(transactionHash)
                .consensusTimestamp(consensusTimestamp)
                .parentConsensusTimestamp(parentConsensusTimestamp)
                .tokenTransferLists(this.tokenTransferLists)
                .assessedCustomFees(this.assessedCustomFees)
                .automaticTokenAssociations(this.automaticTokenAssociations)
                .paidStakingRewards(this.paidStakingRewards)
                .build();

        // create list of sidecar records
        List<TransactionSidecarRecord> transactionSidecarRecords = new ArrayList<>();
        contractStateChanges.stream()
                .map(pair -> new TransactionSidecarRecord(
                        transactionRecord.consensusTimestamp(),
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractActions.stream()
                .map(pair -> new TransactionSidecarRecord(
                        transactionRecord.consensusTimestamp(),
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractBytecodes.stream()
                .map(pair -> new TransactionSidecarRecord(
                        transactionRecord.consensusTimestamp(),
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, pair.getKey())))
                .forEach(transactionSidecarRecords::add);

        return new SingleTransactionRecord(transaction, transactionRecord, transactionSidecarRecords);
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data
    @NonNull
    public SingleTransactionRecordBuilderImpl transaction(@NonNull final Transaction transaction) {
        this.transaction = requireNonNull(transaction, "transaction must not be null");
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transactionBytes(@NonNull final Bytes transactionBytes) {
        this.transactionBytes = requireNonNull(transactionBytes, "transactionBytes must not be null");
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transactionID(@NonNull final TransactionID transactionID) {
        requireNonNull(transactionID, "transactionID must not be null");
        this.transactionRecordBuilder.transactionID(transactionID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl memo(@NonNull final String memo) {
        requireNonNull(memo, "memo must not be null");
        this.transactionRecordBuilder.memo(memo);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord
    @NonNull
    public Instant consensusNow() {
        return consensusNow;
    }

    @Nullable
    public Instant parentConsensusTimestamp() {
        return parentConsensus;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transactionFee(final long transactionFee) {
        this.transactionRecordBuilder.transactionFee(transactionFee);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractCallResult(
            @NonNull final ContractFunctionResult contractCallResult) {
        this.transactionRecordBuilder.contractCallResult(contractCallResult);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractCreateResult(
            @NonNull final ContractFunctionResult contractCreateResult) {
        this.transactionRecordBuilder.contractCreateResult(contractCreateResult);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transferList(@NonNull final TransferList transferList) {
        requireNonNull(transferList, "transferList must not be null");
        this.transactionRecordBuilder.transferList(transferList);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl tokenTransferLists(
            @NonNull final List<TokenTransferList> tokenTransferLists) {
        requireNonNull(tokenTransferLists, "tokenTransferLists must not be null");
        this.tokenTransferLists = tokenTransferLists;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addTokenTransferList(@NonNull final TokenTransferList tokenTransferList) {
        requireNonNull(tokenTransferList, "tokenTransferList must not be null");
        this.tokenTransferLists.add(tokenTransferList);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleRef(@NonNull final ScheduleID scheduleRef) {
        requireNonNull(scheduleRef, "scheduleRef must not be null");
        this.transactionRecordBuilder.scheduleRef(scheduleRef);
        return this;
    }

    @NonNull
    @Override
    public SingleTransactionRecordBuilderImpl assessedCustomFees(
            @NonNull final List<AssessedCustomFee> assessedCustomFees) {
        requireNonNull(assessedCustomFees, "assessedCustomFees must not be null");
        this.assessedCustomFees = assessedCustomFees;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addAssessedCustomFee(@NonNull final AssessedCustomFee assessedCustomFee) {
        requireNonNull(assessedCustomFee, "assessedCustomFee must not be null");
        this.assessedCustomFees.add(assessedCustomFee);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl automaticTokenAssociations(
            @NonNull final List<TokenAssociation> automaticTokenAssociations) {
        requireNonNull(automaticTokenAssociations, "automaticTokenAssociations must not be null");
        this.automaticTokenAssociations = automaticTokenAssociations;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addAutomaticTokenAssociation(
            @NonNull final TokenAssociation automaticTokenAssociation) {
        requireNonNull(automaticTokenAssociation, "automaticTokenAssociation must not be null");
        this.automaticTokenAssociations.add(automaticTokenAssociation);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl alias(@NonNull final Bytes alias) {
        requireNonNull(alias, "alias must not be null");
        this.transactionRecordBuilder.alias(alias);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl ethereumHash(@NonNull final Bytes ethereumHash) {
        requireNonNull(ethereumHash, "ethereumHash must not be null");
        this.transactionRecordBuilder.ethereumHash(ethereumHash);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl paidStakingRewards(
            @NonNull final List<AccountAmount> paidStakingRewards) {
        requireNonNull(paidStakingRewards, "paidStakingRewards must not be null");
        this.paidStakingRewards = paidStakingRewards;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addPaidStakingReward(@NonNull final AccountAmount paidStakingReward) {
        requireNonNull(paidStakingReward, "paidStakingReward must not be null");
        this.paidStakingRewards.add(paidStakingReward);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl entropyNumber(final int num) {
        this.transactionRecordBuilder.prngNumber(num);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes, "The argument 'prngBytes' must not be null");
        this.transactionRecordBuilder.prngBytes(prngBytes);
        return this;
    }

    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl evmAddress(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress, "evmAddress must not be null");
        this.transactionRecordBuilder.evmAddress(evmAddress);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    @NonNull
    public SingleTransactionRecordBuilderImpl status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        this.transactionReceiptBuilder.status(status);
        return this;
    }

    @Override
    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl accountID(@NonNull final AccountID accountID) {
        requireNonNull(accountID, "accountID must not be null");
        this.transactionReceiptBuilder.accountID(accountID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl fileID(@NonNull final FileID fileID) {
        requireNonNull(fileID, "fileID must not be null");
        this.transactionReceiptBuilder.fileID(fileID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractID(@NonNull final ContractID contractID) {
        requireNonNull(contractID, "contractID must not be null");
        this.transactionReceiptBuilder.contractID(contractID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
        requireNonNull(exchangeRate, "exchangeRate must not be null");
        this.transactionReceiptBuilder.exchangeRate(exchangeRate);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicID(@NonNull final TopicID topicID) {
        requireNonNull(topicID, "topicID must not be null");
        this.transactionReceiptBuilder.topicID(topicID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicSequenceNumber(final long topicSequenceNumber) {
        this.transactionReceiptBuilder.topicSequenceNumber(topicSequenceNumber);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicRunningHash(@NonNull final Bytes topicRunningHash) {
        requireNonNull(topicRunningHash, "topicRunningHash must not be null");
        this.transactionReceiptBuilder.topicRunningHash(topicRunningHash);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicRunningHashVersion(final long topicRunningHashVersion) {
        this.transactionReceiptBuilder.topicRunningHashVersion(topicRunningHashVersion);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl tokenID(@NonNull final TokenID tokenID) {
        requireNonNull(tokenID, "tokenID must not be null");
        this.transactionReceiptBuilder.tokenID(tokenID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl newTotalSupply(final long newTotalSupply) {
        this.transactionReceiptBuilder.newTotalSupply(newTotalSupply);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleID(@NonNull final ScheduleID scheduleID) {
        requireNonNull(scheduleID, "scheduleID must not be null");
        this.transactionReceiptBuilder.scheduleID(scheduleID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduledTransactionID(
            @NonNull final TransactionID scheduledTransactionID) {
        this.transactionReceiptBuilder.scheduledTransactionID(scheduledTransactionID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl serialNumbers(@NonNull final List<Long> serialNumbers) {
        requireNonNull(serialNumbers, "serialNumbers must not be null");
        this.serialNumbers = serialNumbers;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addSerialNumber(final long serialNumber) {
        this.serialNumbers.add(serialNumber);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag
    @NonNull
    public SingleTransactionRecordBuilderImpl contractStateChanges(
            @NonNull final List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges = contractStateChanges;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addContractStateChanges(
            @NonNull final ContractStateChanges contractStateChanges, final boolean isMigration) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractActions(
            @NonNull final List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions = contractActions;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addContractActions(
            @NonNull final ContractActions contractActions, final boolean isMigration) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions.add(new AbstractMap.SimpleEntry<>(contractActions, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractBytecodes(
            @NonNull final List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes) {
        requireNonNull(contractBytecodes, "contractBytecodes must not be null");
        this.contractBytecodes = contractBytecodes;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        requireNonNull(contractBytecode, "contractBytecode must not be null");
        this.contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }
}
