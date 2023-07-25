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
    private final TransactionRecord.Builder transactionRecordBuilder = TransactionRecord.newBuilder();

    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private AccountID accountID;
    private FileID fileID;
    private ContractID contractID;
    private ExchangeRateSet exchangeRate;
    private TopicID topicID;
    private long topicSequenceNumber;
    private Bytes topicRunningHash = Bytes.EMPTY;
    private long topicRunningHashVersion;
    private TokenID tokenID;
    private long newTotalSupply;
    private ScheduleID scheduleID;
    private TransactionID scheduledTransactionID;
    private List<Long> serialNumbers = emptyList();
    // Sidecar data, booleans are the migration flag
    public final List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new ArrayList<>();

    public SingleTransactionRecordBuilderImpl(@NonNull final Instant consensusNow) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.parentConsensus = null;
    }

    public SingleTransactionRecordBuilderImpl(
            @NonNull final Instant consensusNow, @NonNull final Instant parentConsensus) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.parentConsensus = requireNonNull(parentConsensus, "parentConsensusTimestamp must not be null");
    }

    @SuppressWarnings("DataFlowIssue")
    public SingleTransactionRecord build() {
        // build
        final var transactionReceipt = new TransactionReceipt(
                status,
                accountID,
                fileID,
                contractID,
                exchangeRate,
                topicID,
                topicSequenceNumber,
                topicRunningHash,
                topicRunningHashVersion,
                tokenID,
                newTotalSupply,
                scheduleID,
                scheduledTransactionID,
                serialNumbers);

        // compute transaction hash: TODO could pass in if we have it calculated else where
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

        this.transactionRecordBuilder.receipt(transactionReceipt);
        this.transactionRecordBuilder.transactionHash(transactionHash);
        this.transactionRecordBuilder.consensusTimestamp(consensusTimestamp);
        this.transactionRecordBuilder.parentConsensusTimestamp(parentConsensusTimestamp);

        final var transactionRecord = this.transactionRecordBuilder.build();

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
        this.transaction = transaction;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transactionBytes(@NonNull final Bytes transactionBytes) {
        this.transactionBytes = transactionBytes;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl transactionID(@NonNull final TransactionID transactionID) {
        this.transactionRecordBuilder.transactionID(transactionID);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl memo(@NonNull final String memo) {
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
        this.transactionRecordBuilder.transferList(transferList);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl tokenTransferLists(
            @NonNull final List<TokenTransferList> tokenTransferLists) {
        this.transactionRecordBuilder.tokenTransferLists(tokenTransferLists);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleRef(@NonNull final ScheduleID scheduleRef) {
        this.transactionRecordBuilder.scheduleRef(scheduleRef);
        return this;
    }

    @NonNull
    @Override
    public SingleTransactionRecordBuilderImpl assessedCustomFees(
            @NonNull final List<AssessedCustomFee> assessedCustomFees) {
        this.transactionRecordBuilder.assessedCustomFees(assessedCustomFees);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl automaticTokenAssociations(
            @NonNull final List<TokenAssociation> automaticTokenAssociations) {
        this.transactionRecordBuilder.automaticTokenAssociations(automaticTokenAssociations);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl alias(@NonNull final Bytes alias) {
        this.transactionRecordBuilder.alias(alias);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl ethereumHash(@NonNull final Bytes ethereumHash) {
        this.transactionRecordBuilder.ethereumHash(ethereumHash);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl paidStakingRewards(
            @NonNull final List<AccountAmount> paidStakingRewards) {
        this.transactionRecordBuilder.paidStakingRewards(paidStakingRewards);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl entropyNumber(final int num) {
        this.transactionRecordBuilder.prngNumber(num);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes, "The argument 'entropyBytes' must not be null");
        this.transactionRecordBuilder.prngBytes(prngBytes);
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public OneOf<TransactionRecord.EntropyOneOfType> entropy() {
        return this.transactionRecordBuilder.build().entropy();
    }

    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl evmAddress(@NonNull final Bytes evmAddress) {
        this.transactionRecordBuilder.evmAddress(evmAddress);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    @NonNull
    public SingleTransactionRecordBuilderImpl status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        return this;
    }

    @Override
    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl accountID(@NonNull final AccountID accountID) {
        this.accountID = accountID;
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public AccountID accountID() {
        return accountID;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public TokenID tokenID() {
        return tokenID;
    }

    public SingleTransactionRecordBuilderImpl fileID(FileID fileID) {
    @NonNull
    public SingleTransactionRecordBuilderImpl fileID(@NonNull final FileID fileID) {
        this.fileID = fileID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl contractID(@NonNull final ContractID contractID) {
        this.contractID = contractID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
        this.exchangeRate = exchangeRate;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicID(@NonNull final TopicID topicID) {
        this.topicID = topicID;
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public TopicID topicID() {
        return topicID;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicSequenceNumber(final long topicSequenceNumber) {
        this.topicSequenceNumber = topicSequenceNumber;
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    public long topicSequenceNumber() {
        return topicSequenceNumber;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicRunningHash(@NonNull final Bytes topicRunningHash) {
        this.topicRunningHash = topicRunningHash;
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public Bytes topicRunningHash() {
        return topicRunningHash;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl topicRunningHashVersion(final long topicRunningHashVersion) {
        this.topicRunningHashVersion = topicRunningHashVersion;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl tokenID(@NonNull final TokenID tokenID) {
        this.tokenID = tokenID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl newTotalSupply(final long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleID(@NonNull final ScheduleID scheduleID) {
        this.scheduleID = scheduleID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl scheduledTransactionID(
            @NonNull final TransactionID scheduledTransactionID) {
        this.scheduledTransactionID = scheduledTransactionID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl serialNumbers(@NonNull final List<Long> serialNumbers) {
        this.serialNumbers = serialNumbers;
        return this;
    }

    /**
     * @deprecated this method is only used temporarily during the migration
     */
    @Deprecated(forRemoval = true)
    @Nullable
    public List<Long> serialNumbers() {
        return serialNumbers;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag
    @NonNull
    public SingleTransactionRecordBuilderImpl addContractStateChanges(
            @NonNull final ContractStateChanges contractStateChanges, final boolean isMigration) {
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addContractAction(
            @NonNull final ContractActions contractAction, final boolean isMigration) {
        contractActions.add(new AbstractMap.SimpleEntry<>(contractAction, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilderImpl addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }
}
