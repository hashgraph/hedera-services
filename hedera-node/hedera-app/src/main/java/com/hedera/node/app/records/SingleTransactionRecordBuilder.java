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

package com.hedera.node.app.records;

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
import com.hedera.hapi.node.transaction.TransactionRecord.EntropyOneOfType;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.RecordStreamItem;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.records.TokenCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.records.SingleTransactionRecord;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;

/**
 * A custom builder for SingleTransactionRecord.
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public class SingleTransactionRecordBuilder
        implements ConsensusCreateTopicRecordBuilder,
                ConsensusSubmitMessageRecordBuilder,
                CreateFileRecordBuilder,
                CryptoCreateRecordBuilder,
                PrngRecordBuilder,
                TokenMintRecordBuilder,
                TokenCreateRecordBuilder {
    // base transaction data
    private Transaction transaction;
    private Bytes transactionBytes;
    // fields needed for TransactionRecord
    private final Instant consensusNow;
    private long transactionFee;
    private ContractFunctionResult contractCallResult;
    private ContractFunctionResult contractCreateResult;
    private TransferList transferList;
    private List<TokenTransferList> tokenTransferLists;
    private ScheduleID scheduleRef;
    private List<AssessedCustomFee> assessedCustomFees;
    private List<TokenAssociation> automaticTokenAssociations;
    private Instant parentConsensusTimestamp;
    private Bytes alias;
    private Bytes ethereumHash;
    private List<AccountAmount> paidStakingRewards;
    private OneOf<TransactionRecord.EntropyOneOfType> entropy;
    private Bytes evmAddress;
    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private AccountID accountID;
    private FileID fileID;
    private ContractID contractID;
    private ExchangeRateSet exchangeRate;
    private TopicID topicID;
    private long topicSequenceNumber;
    private Bytes topicRunningHash;
    private long topicRunningHashVersion;
    private TokenID tokenID;
    private long newTotalSupply;
    private ScheduleID scheduleID;
    private TransactionID scheduledTransactionID;
    private List<Long> serialNumbers;
    // Sidecar data, booleans are the migration flag
    public final List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new ArrayList<>();
    public final List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new ArrayList<>();

    public SingleTransactionRecordBuilder(@NonNull final Instant consensusNow) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.parentConsensusTimestamp = consensusNow;
    }

    @SuppressWarnings("DataFlowIssue")
    public SingleTransactionRecord build() {
        // compute transaction hash: TODO could pass in if we have it calculated else where
        final Timestamp consensusTimestamp = HapiUtils.asTimestamp(consensusNow);
        final byte[] transactionBytes = new byte[(int) this.transactionBytes.length()];
        this.transactionBytes.getBytes(0, transactionBytes);
        final Bytes transactionHash = Bytes.wrap(new Hash(transactionBytes).getValue());
        // create body one of
        OneOf<TransactionRecord.BodyOneOfType> body = new OneOf<>(TransactionRecord.BodyOneOfType.UNSET, null);
        if (contractCallResult != null)
            body = new OneOf<>(TransactionRecord.BodyOneOfType.CONTRACT_CALL_RESULT, contractCallResult);
        if (contractCreateResult != null)
            body = new OneOf<>(TransactionRecord.BodyOneOfType.CONTRACT_CREATE_RESULT, contractCreateResult);
        // create list of sidecar records
        List<TransactionSidecarRecord> transactionSidecarRecords = new ArrayList<>();
        contractStateChanges.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractActions.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractBytecodes.stream()
                .map(pair -> new TransactionSidecarRecord(
                        consensusTimestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        // build
        return new SingleTransactionRecord(
                new RecordStreamItem(
                        transaction,
                        new TransactionRecord(
                                new TransactionReceipt(
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
                                        serialNumbers),
                                transactionHash,
                                consensusTimestamp,
                                transaction.body().transactionID(),
                                transaction.body().memo(),
                                transactionFee,
                                body,
                                transferList,
                                tokenTransferLists,
                                scheduleRef,
                                assessedCustomFees,
                                automaticTokenAssociations,
                                HapiUtils.asTimestamp(parentConsensusTimestamp),
                                alias,
                                ethereumHash,
                                paidStakingRewards,
                                entropy,
                                evmAddress)),
                transactionSidecarRecords);
    }
    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data
    @NonNull
    public SingleTransactionRecordBuilder transaction(Transaction transaction, Bytes transactionBytes) {
        this.transaction = transaction;
        this.transactionBytes = transactionBytes;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord

    @NonNull
    public Instant consensusNow() {
        return consensusNow;
    }

    @NonNull
    public SingleTransactionRecordBuilder transactionFee(long transactionFee) {
        this.transactionFee = transactionFee;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder contractCallResult(ContractFunctionResult contractCallResult) {
        this.contractCallResult = contractCallResult;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder contractCreateResult(ContractFunctionResult contractCreateResult) {
        this.contractCreateResult = contractCreateResult;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder transferList(TransferList transferList) {
        this.transferList = transferList;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder tokenTransferLists(List<TokenTransferList> tokenTransferLists) {
        this.tokenTransferLists = tokenTransferLists;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder scheduleRef(ScheduleID scheduleRef) {
        this.scheduleRef = scheduleRef;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder assessedCustomFees(List<AssessedCustomFee> assessedCustomFees) {
        this.assessedCustomFees = assessedCustomFees;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder automaticTokenAssociations(
            List<TokenAssociation> automaticTokenAssociations) {
        this.automaticTokenAssociations = automaticTokenAssociations;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder parentConsensusTimestamp(@NonNull Instant parentConsensusTimestamp) {
        this.parentConsensusTimestamp =
                requireNonNull(parentConsensusTimestamp, "parentConsensusTimestamp must not be null");
        return this;
    }

    @NonNull
    public Instant parentConsensusTimestamp() {
        return parentConsensusTimestamp;
    }

    @NonNull
    public SingleTransactionRecordBuilder alias(Bytes alias) {
        this.alias = alias;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder ethereumHash(Bytes ethereumHash) {
        this.ethereumHash = ethereumHash;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder paidStakingRewards(List<AccountAmount> paidStakingRewards) {
        this.paidStakingRewards = paidStakingRewards;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder entropyNumber(final int num) {
        this.entropy = new OneOf<>(EntropyOneOfType.PRNG_NUMBER, num);
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes, "The argument 'entropyBytes' must not be null");
        this.entropy = new OneOf<>(EntropyOneOfType.PRNG_BYTES, prngBytes);
        return this;
    }

    @Nullable
    public OneOf<TransactionRecord.EntropyOneOfType> entropy() {
        return entropy;
    }

    @NonNull
    public SingleTransactionRecordBuilder evmAddress(Bytes evmAddress) {
        this.evmAddress = evmAddress;
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    @NonNull
    public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        return this;
    }

    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    @NonNull
    public SingleTransactionRecordBuilder accountID(@NonNull final AccountID accountID) {
        this.accountID = requireNonNull(accountID, "accountID must not be null");
        return this;
    }

    @Nullable
    public AccountID accountID() {
        return accountID;
    }

    @Nullable
    public TokenID tokenID() {
        return tokenID;
    }

    @NonNull
    public SingleTransactionRecordBuilder fileID(@NonNull FileID fileID) {
        this.fileID = requireNonNull(fileID, "fileID must not be null");
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder contractID(ContractID contractID) {
        this.contractID = contractID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder exchangeRate(ExchangeRateSet exchangeRate) {
        this.exchangeRate = exchangeRate;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder topicID(@NonNull final TopicID topicID) {
        this.topicID = requireNonNull(topicID, "topicID must not be null");
        return this;
    }

    @Nullable
    public TopicID topicID() {
        return topicID;
    }

    @NonNull
    public SingleTransactionRecordBuilder topicSequenceNumber(long topicSequenceNumber) {
        this.topicSequenceNumber = topicSequenceNumber;
        return this;
    }

    public long topicSequenceNumber() {
        return topicSequenceNumber;
    }

    @NonNull
    public SingleTransactionRecordBuilder topicRunningHash(@NonNull final Bytes topicRunningHash) {
        this.topicRunningHash = requireNonNull(topicRunningHash, "topicRunningHash must not be null");
        return this;
    }

    @Nullable
    public Bytes topicRunningHash() {
        return topicRunningHash;
    }

    @NonNull
    public SingleTransactionRecordBuilder topicRunningHashVersion(long topicRunningHashVersion) {
        this.topicRunningHashVersion = topicRunningHashVersion;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder tokenID(@NonNull TokenID tokenID) {
        this.tokenID = requireNonNull(tokenID, "tokenID must not be null");
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder newTotalSupply(long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder scheduleID(ScheduleID scheduleID) {
        this.scheduleID = scheduleID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder scheduledTransactionID(TransactionID scheduledTransactionID) {
        this.scheduledTransactionID = scheduledTransactionID;
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder serialNumbers(@NonNull List<Long> serialNumbers) {
        this.serialNumbers = requireNonNull(serialNumbers, "serialNumbers must not be null");
        return this;
    }

    @Nullable
    public List<Long> serialNumbers() {
        return serialNumbers;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag
    @NonNull
    public SingleTransactionRecordBuilder addContractStateChanges(
            ContractStateChanges contractStateChanges, boolean isMigration) {
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder addContractAction(ContractActions contractAction, boolean isMigration) {
        contractActions.add(new AbstractMap.SimpleEntry<>(contractAction, isMigration));
        return this;
    }

    @NonNull
    public SingleTransactionRecordBuilder addContractBytecode(ContractBytecode contractBytecode, boolean isMigration) {
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }
}
