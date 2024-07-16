/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.state.logging.TransactionStateLogger.logEndTransactionRecord;

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
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.hapi.util.HapiUtils;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord.TransactionOutputs;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.DigestType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

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
public class SingleTransactionBlockBuilderImpl
        implements SingleTransactionRecordBuilder,
                ConsensusSubmitMessageRecordBuilder {
    private static final Comparator<TokenAssociation> TOKEN_ASSOCIATION_COMPARATOR =
            Comparator.<TokenAssociation>comparingLong(a -> a.tokenId().tokenNum())
                    .thenComparingLong(a -> a.accountIdOrThrow().accountNum());
    // base transaction data
    private Transaction transaction;
    private Bytes transactionBytes = Bytes.EMPTY;
    // fields needed for TransactionRecord
    // Mutable because the provisional consensus timestamp assigned on dispatch could
    // change when removable records appear "between" this record and the parent record
    private Instant consensusNow;
    private Instant parentConsensus;
    private TransactionID transactionID;
    private List<TokenTransferList> tokenTransferLists = new LinkedList<>();
    private List<AssessedCustomFee> assessedCustomFees = new LinkedList<>();
    private List<TokenAssociation> automaticTokenAssociations = new LinkedList<>();

    private List<AccountAmount> paidStakingRewards = new LinkedList<>();
    private final TransactionRecord.Builder transactionRecordBuilder = TransactionRecord.newBuilder();
    private TransferList transferList = TransferList.DEFAULT;

    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private ExchangeRateSet exchangeRate = ExchangeRateSet.DEFAULT;
    private long newTotalSupply = 0L;
    private final TransactionReceipt.Builder transactionReceiptBuilder = TransactionReceipt.newBuilder();
    // Sidecar data, booleans are the migration flag
    private List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges = new LinkedList<>();
    private List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new LinkedList<>();
    private List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new LinkedList<>();

    // Fields that are not in TransactionRecord, but are needed for computing staking rewards
    // These are not persisted to the record file
    private final Map<AccountID, AccountID> deletedAccountBeneficiaries = new HashMap<>();

    // A set of ids that should be explicitly considered as in a "reward situation",
    // despite the canonical definition of a reward situation; needed for mono-service
    // fidelity only
    @Nullable
    private Set<AccountID> explicitRewardReceiverIds;

    // While the fee is sent to the underlying builder all the time, it is also cached here because, as of today,
    // there is no way to get the transaction fee from the PBJ object.
    private long transactionFee;
    private ContractFunctionResult contractFunctionResult;

    // Used for some child records builders.
    private final ReversingBehavior reversingBehavior;

    // Used to customize the externalized form of a dispatched child transaction, right before
    // its record stream item is built; lets the contract service externalize certain dispatched
    // CryptoCreate transactions as ContractCreate synthetic transactions
    private final ExternalizedRecordCustomizer customizer;

    private TokenID tokenID;
    private TokenType tokenType;

    /**
     * Possible behavior of a {@link SingleTransactionRecord} when a parent transaction fails,
     * and it is asked to be reverted
     */
    public enum ReversingBehavior {
        /**
         * Changes are not committed. The record is kept in the record stream,
         * but the status is set to {@link ResponseCodeEnum#REVERTED_SUCCESS}
         */
        REVERSIBLE,

        /**
         * Changes are not committed and the record is removed from the record stream.
         */
        REMOVABLE,

        /**
         * Changes are committed independent of the user and parent transactions.
         */
        IRREVERSIBLE
    }

    /**
     * Creates new transaction record builder where reversion will leave its record in the stream
     * with either a failure status or {@link ResponseCodeEnum#REVERTED_SUCCESS}.
     *
     * @param consensusNow the consensus timestamp for the transaction
     */
    public SingleTransactionBlockBuilderImpl(@NonNull final Instant consensusNow) {
        this(consensusNow, ReversingBehavior.REVERSIBLE);
    }

    /**
     * Creates new transaction record builder.
     *
     * @param consensusNow the consensus timestamp for the transaction
     * @param reversingBehavior the reversing behavior (see {@link RecordListBuilder}
     */
    public SingleTransactionBlockBuilderImpl(
            @NonNull final Instant consensusNow, final ReversingBehavior reversingBehavior) {
        this(consensusNow, reversingBehavior, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER);
    }

    /**
     * Creates new transaction record builder with both explicit reversing behavior and
     * transaction construction finishing.
     *
     * @param consensusNow the consensus timestamp for the transaction
     * @param reversingBehavior the reversing behavior (see {@link RecordListBuilder}
     */
    public SingleTransactionBlockBuilderImpl(
            @NonNull final Instant consensusNow,
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        this.consensusNow = requireNonNull(consensusNow, "consensusNow must not be null");
        this.reversingBehavior = requireNonNull(reversingBehavior, "reversingBehavior must not be null");
        this.customizer = requireNonNull(customizer, "customizer must not be null");
    }

    /**
     * Builds single transaction record.
     *
     * @return the transaction record
     */
    public SingleTransactionRecord build() {
        if (customizer != NOOP_EXTERNALIZED_RECORD_CUSTOMIZER) {
            transaction = customizer.apply(transaction);
            transactionBytes = transaction.signedTransactionBytes();
        }
//        final var builder = transactionReceiptBuilder.serialNumbers(serialNumbers);
        final var builder = transactionReceiptBuilder;
        // FUTURE : In mono-service exchange rate is not set in preceding child records.
        // This should be changed after differential testing
        if (exchangeRate != null && exchangeRate.hasCurrentRate() && exchangeRate.hasNextRate()) {
            builder.exchangeRate(exchangeRate);
        }
        final var transactionReceipt = builder.build();

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

        // sort the automatic associations to match the order of mono-service records
        final var newAutomaticTokenAssociations = new ArrayList<>(automaticTokenAssociations);
        if (!automaticTokenAssociations.isEmpty()) {
            newAutomaticTokenAssociations.sort(TOKEN_ASSOCIATION_COMPARATOR);
        }

        final var transactionRecord = transactionRecordBuilder
                .transactionID(transactionID)
                .receipt(transactionReceipt)
                .transactionHash(transactionHash)
                .consensusTimestamp(consensusTimestamp)
                .parentConsensusTimestamp(parentConsensusTimestamp)
                .transferList(transferList)
                .tokenTransferLists(tokenTransferLists)
                .assessedCustomFees(assessedCustomFees)
                .automaticTokenAssociations(newAutomaticTokenAssociations)
                .paidStakingRewards(paidStakingRewards)
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

        // Log end of user transaction to transaction state log
        logEndTransactionRecord(transactionID, transactionRecord);

        return new SingleTransactionRecord(
                transaction,
                transactionRecord,
                transactionSidecarRecords,
                new TransactionOutputs(tokenType, transaction.body().data().kind()));
    }

    public void nullOutSideEffectFields() {
//        serialNumbers.clear();
        tokenTransferLists.clear();
        automaticTokenAssociations.clear();
        transferList = TransferList.DEFAULT;
        paidStakingRewards.clear();
        assessedCustomFees.clear();

        newTotalSupply = 0L;
        contractFunctionResult = null;

        transactionReceiptBuilder.accountID((AccountID) null);
        transactionReceiptBuilder.contractID((ContractID) null);
        transactionReceiptBuilder.fileID((FileID) null);
        transactionReceiptBuilder.tokenID((TokenID) null);
        transactionReceiptBuilder.scheduleID((ScheduleID) null);
        transactionReceiptBuilder.scheduledTransactionID((TransactionID) null);
        transactionReceiptBuilder.topicRunningHash(Bytes.EMPTY);
        transactionReceiptBuilder.newTotalSupply(0L);
        transactionReceiptBuilder.topicRunningHashVersion(0L);
        transactionReceiptBuilder.topicSequenceNumber(0L);
        // Note that internal contract creations are removed instead of reversed
        transactionRecordBuilder.scheduleRef((ScheduleID) null);
        transactionRecordBuilder.alias(Bytes.EMPTY);
        transactionRecordBuilder.ethereumHash(Bytes.EMPTY);
        transactionRecordBuilder.evmAddress(Bytes.EMPTY);
    }

    public ReversingBehavior reversingBehavior() {
        return reversingBehavior;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data

    public SingleTransactionBlockBuilderImpl parentConsensus(@NonNull final Instant parentConsensus) {
        this.parentConsensus = requireNonNull(parentConsensus, "parentConsensus must not be null");
        return this;
    }

    public SingleTransactionBlockBuilderImpl consensusTimestamp(@NonNull final Instant now) {
        this.consensusNow = requireNonNull(now, "consensus time must not be null");
        return this;
    }

    /**
     * Sets the transaction bytes that will be used to compute the transaction hash.
     *
     * @param transactionBytes the transaction bytes
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl transactionBytes(@NonNull final Bytes transactionBytes) {
        this.transactionBytes = requireNonNull(transactionBytes, "transactionBytes must not be null");
        return this;
    }

    /**
     * Gets the {@link TransactionID} that is currently set.
     *
     * @return the {@link TransactionID}
     */
    public TransactionID transactionID() {
        return transactionID;
    }

    /**
     * Sets the transaction ID.
     *
     * @param transactionID the transaction ID
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl transactionID(@NonNull final TransactionID transactionID) {
        this.transactionID = requireNonNull(transactionID, "transactionID must not be null");
        return this;
    }

    /**
     * When we update nonce on the record, we need to update the body as well with the same transactionID.
     *
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl syncBodyIdFromRecordId() {
        final var newTransactionID = transactionID;
        final var body =
                inProgressBody().copyBuilder().transactionID(newTransactionID).build();
        this.transaction = SingleTransactionRecordBuilder.transactionWith(body);
        this.transactionBytes = transaction.signedTransactionBytes();
        return this;
    }

    /**
     * Sets the memo.
     *
     * @param memo the memo
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl memo(@NonNull final String memo) {
        requireNonNull(memo, "memo must not be null");
        transactionRecordBuilder.memo(memo);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord

    /**
     * Gets the transaction object.
     *
     * @return the transaction object
     */
    @NonNull
    public Transaction transaction() {
        return transaction;
    }

    /**
     * Gets the consensus instant.
     *
     * @return the consensus instant
     */
    @NonNull
    public Instant consensusNow() {
        return consensusNow;
    }

    /**
     * Gets the parent consensus instant.
     *
     * @return the parent consensus instant
     */
    @Nullable
    public Instant parentConsensusTimestamp() {
        return parentConsensus;
    }

    @Override
    public long transactionFee() {
        return transactionFee;
    }

    /**
     * Adds a tokenTransferList.
     *
     * @param tokenTransferList the tokenTransferList
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addTokenTransferList(@NonNull final TokenTransferList tokenTransferList) {
        requireNonNull(tokenTransferList, "tokenTransferList must not be null");
        tokenTransferLists.add(tokenTransferList);
        return this;
    }

    /**
     * Adds an assessedCustomFee.
     *
     * @param assessedCustomFee the assessedCustomFee
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addAssessedCustomFee(@NonNull final AssessedCustomFee assessedCustomFee) {
        requireNonNull(assessedCustomFee, "assessedCustomFee must not be null");
        assessedCustomFees.add(assessedCustomFee);
        return this;
    }

    /**
     * Sets the automaticTokenAssociations.
     *
     * @param automaticTokenAssociations the automaticTokenAssociations
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl automaticTokenAssociations(
            @NonNull final List<TokenAssociation> automaticTokenAssociations) {
        requireNonNull(automaticTokenAssociations, "automaticTokenAssociations must not be null");
        this.automaticTokenAssociations = automaticTokenAssociations;
        return this;
    }

    /**
     * Adds an automaticTokenAssociation.
     *
     * @param automaticTokenAssociation the automaticTokenAssociation
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addAutomaticTokenAssociation(
            @NonNull final TokenAssociation automaticTokenAssociation) {
        requireNonNull(automaticTokenAssociation, "automaticTokenAssociation must not be null");
        automaticTokenAssociations.add(automaticTokenAssociation);
        return this;
    }

    /**
     * Sets the alias.
     *
     * @param alias the alias
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl alias(@NonNull final Bytes alias) {
        requireNonNull(alias, "alias must not be null");
        transactionRecordBuilder.alias(alias);
        return this;
    }

    /**
     * Sets the ethereum hash.
     *
     * @param ethereumHash the ethereum hash
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl ethereumHash(@NonNull final Bytes ethereumHash) {
        requireNonNull(ethereumHash, "ethereumHash must not be null");
        transactionRecordBuilder.ethereumHash(ethereumHash);
        return this;
    }

    /**
     * Sets the paidStakingRewards.
     *
     * @param paidStakingRewards the paidStakingRewards
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl paidStakingRewards(
            @NonNull final List<AccountAmount> paidStakingRewards) {
        requireNonNull(paidStakingRewards, "paidStakingRewards must not be null");
        this.paidStakingRewards = paidStakingRewards;
        return this;
    }

    /**
     * Adds a paidStakingReward.
     *
     * @param paidStakingReward the paidStakingReward
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addPaidStakingReward(@NonNull final AccountAmount paidStakingReward) {
        requireNonNull(paidStakingReward, "paidStakingReward must not be null");
        paidStakingRewards.add(paidStakingReward);
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    /**
     * Sets the receipt status.
     *
     * @param status the receipt status
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionBlockBuilderImpl status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        transactionReceiptBuilder.status(status);
        return this;
    }

    /**
     * Gets the receipt status.
     *
     * @return the receipt status
     */
    @Override
    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    /**
     * Returns if the builder has a ContractFunctionResult set.
     *
     * @return the receipt status
     */
    public boolean hasContractResult() {
        return this.contractFunctionResult != null;
    }

    public long getGasUsedForContractTxn() {
        return this.contractFunctionResult.gasUsed();
    }

    /**
     * Gets the {@link ExchangeRateSet} that is currently set for the receipt.
     *
     * @return the {@link ExchangeRateSet}
     */
    @NonNull
    public ExchangeRateSet exchangeRate() {
        return exchangeRate;
    }

    /**
     * Sets the receipt exchange rate.
     *
     * @param exchangeRate the {@link ExchangeRateSet} for the receipt
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
        requireNonNull(exchangeRate, "exchangeRate must not be null");
        this.exchangeRate = exchangeRate;
        return this;
    }

    /**
     * Sets the receipt topicSequenceNumber.
     *
     * @param topicSequenceNumber the topicSequenceNumber for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionBlockBuilderImpl topicSequenceNumber(final long topicSequenceNumber) {
        transactionReceiptBuilder.topicSequenceNumber(topicSequenceNumber);
        return this;
    }

    /**
     * Sets the receipt topicRunningHash.
     *
     * @param topicRunningHash the topicRunningHash for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionBlockBuilderImpl topicRunningHash(@NonNull final Bytes topicRunningHash) {
        requireNonNull(topicRunningHash, "topicRunningHash must not be null");
        transactionReceiptBuilder.topicRunningHash(topicRunningHash);
        return this;
    }

    /**
     * Sets the receipt topicRunningHashVersion.
     *
     * @param topicRunningHashVersion the topicRunningHashVersion for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionBlockBuilderImpl topicRunningHashVersion(final long topicRunningHashVersion) {
        transactionReceiptBuilder.topicRunningHashVersion(topicRunningHashVersion);
        return this;
    }

    public TokenID tokenID() {
        return tokenID;
    }

    /**
     * Sets the receipt newTotalSupply.
     *
     * @param newTotalSupply the newTotalSupply for the receipt
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl newTotalSupply(final long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        transactionReceiptBuilder.newTotalSupply(newTotalSupply);
        return this;
    }

    public long getNewTotalSupply() {
        return newTotalSupply;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag

    /**
     * Sets the contractStateChanges which are part of sidecar records.
     *
     * @param contractStateChanges the contractStateChanges
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl contractStateChanges(
            @NonNull final List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges = contractStateChanges;
        return this;
    }

    /**
     * Adds contractStateChanges to sidecar records.
     *
     * @param contractStateChanges the contractStateChanges to add
     * @param isMigration flag indicating whether sidecar is from migration
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addContractStateChanges(
            @NonNull final ContractStateChanges contractStateChanges, final boolean isMigration) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    /**
     * Sets the contractActions which are part of sidecar records.
     *
     * @param contractActions the contractActions
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl contractActions(
            @NonNull final List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions = contractActions;
        return this;
    }

    /**
     * Adds contractActions to sidecar records.
     *
     * @param contractActions the contractActions to add
     * @param isMigration flag indicating whether sidecar is from migration
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addContractActions(
            @NonNull final ContractActions contractActions, final boolean isMigration) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions.add(new AbstractMap.SimpleEntry<>(contractActions, isMigration));
        return this;
    }

    /**
     * Sets the contractBytecodes which are part of sidecar records.
     *
     * @param contractBytecodes the contractBytecodes
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl contractBytecodes(
            @NonNull final List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes) {
        requireNonNull(contractBytecodes, "contractBytecodes must not be null");
        this.contractBytecodes = contractBytecodes;
        return this;
    }

    /**
     * Adds contractBytecodes to sidecar records.
     *
     * @param contractBytecode the contractBytecode to add
     * @param isMigration flag indicating whether sidecar is from migration
     * @return the builder
     */
    @NonNull
    public SingleTransactionBlockBuilderImpl addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        requireNonNull(contractBytecode, "contractBytecode must not be null");
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }

    // ------------- Information needed by token service for redirecting staking rewards to appropriate accounts

    /**
     * Returns the in-progress {@link ContractFunctionResult}.
     *
     * @return the in-progress {@link ContractFunctionResult}
     */
    public ContractFunctionResult contractFunctionResult() {
        return contractFunctionResult;
    }

    @Override
    public @NonNull TransactionBody transactionBody() {
        return inProgressBody();
    }

    /**
     * Returns the in-progress {@link TransactionBody}.
     *
     * @return the in-progress {@link TransactionBody}
     */
    private TransactionBody inProgressBody() {
        try {
            final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                    transaction.signedTransactionBytes().toReadableSequentialData());
            return TransactionBody.PROTOBUF.parse(signedTransaction.bodyBytes().toReadableSequentialData());
        } catch (Exception e) {
            throw new IllegalStateException("Record being built for unparseable transaction", e);
        }
    }

    /**
     * Returns the staking rewards paid in this transaction.
     *
     * @return the staking rewards paid in this transaction
     */
    public List<AccountAmount> getPaidStakingRewards() {
        return paidStakingRewards;
    }

    @Override
    public String toString() {
        return "SingleTransactionRecordBuilderImpl{" + "transaction="
                + transaction + ", transactionBytes="
                + transactionBytes + ", consensusNow="
                + consensusNow + ", parentConsensus="
                + parentConsensus + ", transactionID="
                + transactionID + ", tokenTransferLists="
                + tokenTransferLists + ", assessedCustomFees="
                + assessedCustomFees + ", automaticTokenAssociations="
                + automaticTokenAssociations + ", paidStakingRewards="
                + paidStakingRewards + ", transactionRecordBuilder="
                + transactionRecordBuilder + ", transferList="
                + transferList + ", status="
                + status + ", exchangeRate="
                + exchangeRate + ", serialNumbers="
//                + serialNumbers + ", newTotalSupply="
                + newTotalSupply + ", transactionReceiptBuilder="
                + transactionReceiptBuilder + ", contractStateChanges="
                + contractStateChanges + ", contractActions="
                + contractActions + ", contractBytecodes="
                + contractBytecodes + ", deletedAccountBeneficiaries="
                + deletedAccountBeneficiaries + ", explicitRewardReceiverIds="
                + explicitRewardReceiverIds + ", transactionFee="
                + transactionFee + ", contractFunctionResult="
                + contractFunctionResult + ", reversingBehavior="
                + reversingBehavior + ", customizer="
                + customizer + ", tokenID="
                + tokenID + ", tokenType="
                + tokenType + ", inProgressBody="
                + inProgressBody() + '}';
    }
}
