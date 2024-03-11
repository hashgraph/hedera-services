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
import static java.util.Collections.emptySet;
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
import com.hedera.hapi.node.base.TopicID;
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
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicRecordBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractDeleteRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractOperationRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractUpdateRecordBuilder;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileRecordBuilder;
import com.hedera.node.app.service.schedule.ScheduleRecordBuilder;
import com.hedera.node.app.service.token.api.FeeRecordBuilder;
import com.hedera.node.app.service.token.records.ChildRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoDeleteRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.GenesisAccountRecordBuilder;
import com.hedera.node.app.service.token.records.NodeStakeUpdateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAccountWipeRecordBuilder;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import com.hedera.node.app.service.token.records.TokenCreateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.state.SingleTransactionRecord.TransactionOutputs;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
                ChildRecordBuilder,
                PrngRecordBuilder,
                ScheduleRecordBuilder,
                TokenMintRecordBuilder,
                TokenBurnRecordBuilder,
                TokenCreateRecordBuilder,
                ContractCreateRecordBuilder,
                ContractCallRecordBuilder,
                ContractUpdateRecordBuilder,
                EthereumTransactionRecordBuilder,
                CryptoDeleteRecordBuilder,
                TokenUpdateRecordBuilder,
                NodeStakeUpdateRecordBuilder,
                FeeRecordBuilder,
                ContractDeleteRecordBuilder,
                GenesisAccountRecordBuilder,
                ContractOperationRecordBuilder,
                TokenAccountWipeRecordBuilder,
                CryptoUpdateRecordBuilder {
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
    private List<Long> serialNumbers = new LinkedList<>();
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
    public SingleTransactionRecordBuilderImpl(@NonNull final Instant consensusNow) {
        this(consensusNow, ReversingBehavior.REVERSIBLE);
    }

    /**
     * Creates new transaction record builder.
     *
     * @param consensusNow the consensus timestamp for the transaction
     * @param reversingBehavior the reversing behavior (see {@link RecordListBuilder}
     */
    public SingleTransactionRecordBuilderImpl(
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
    public SingleTransactionRecordBuilderImpl(
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
        final var builder = transactionReceiptBuilder.serialNumbers(serialNumbers);
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
                transaction, transactionRecord, transactionSidecarRecords, new TransactionOutputs(tokenType));
    }

    public void nullOutSideEffectFields() {
        serialNumbers.clear();
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

    public SingleTransactionRecordBuilderImpl parentConsensus(@NonNull final Instant parentConsensus) {
        this.parentConsensus = requireNonNull(parentConsensus, "parentConsensus must not be null");
        return this;
    }

    public SingleTransactionRecordBuilderImpl consensusTimestamp(@NonNull final Instant now) {
        this.consensusNow = requireNonNull(now, "consensus time must not be null");
        return this;
    }

    /**
     * Sets the transaction.
     *
     * @param transaction the transaction
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl transaction(@NonNull final Transaction transaction) {
        this.transaction = requireNonNull(transaction, "transaction must not be null");
        return this;
    }

    /**
     * Sets the transaction bytes that will be used to compute the transaction hash.
     *
     * @param transactionBytes the transaction bytes
     * @return the builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl transactionBytes(@NonNull final Bytes transactionBytes) {
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
    public SingleTransactionRecordBuilderImpl transactionID(@NonNull final TransactionID transactionID) {
        this.transactionID = requireNonNull(transactionID, "transactionID must not be null");
        return this;
    }

    /**
     * When we update nonce on the record, we need to update the body as well with the same transactionID.
     *
     * @return the builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl syncBodyIdFromRecordId() {
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
    public SingleTransactionRecordBuilderImpl memo(@NonNull final String memo) {
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
     * Sets the consensus transaction fee.
     *
     * @param transactionFee the transaction fee
     * @return the builder
     */
    @NonNull
    @Override
    public SingleTransactionRecordBuilderImpl transactionFee(final long transactionFee) {
        this.transactionFee = transactionFee;
        this.transactionRecordBuilder.transactionFee(transactionFee);
        return this;
    }

    @Override
    public void trackExplicitRewardSituation(@NonNull final AccountID accountId) {
        if (explicitRewardReceiverIds == null) {
            explicitRewardReceiverIds = new LinkedHashSet<>();
        }
        explicitRewardReceiverIds.add(accountId);
    }

    @Override
    public Set<AccountID> explicitRewardSituationIds() {
        return explicitRewardReceiverIds != null ? explicitRewardReceiverIds : emptySet();
    }

    /**
     * Sets the body to contractCall result.
     *
     * @param contractCallResult the contractCall result
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl contractCallResult(
            @Nullable final ContractFunctionResult contractCallResult) {
        transactionRecordBuilder.contractCallResult(contractCallResult);
        this.contractFunctionResult = contractCallResult;
        return this;
    }

    /**
     * Sets the body to contractCreateResult result.
     *
     * @param contractCreateResult the contractCreate result
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl contractCreateResult(
            @Nullable ContractFunctionResult contractCreateResult) {
        transactionRecordBuilder.contractCreateResult(contractCreateResult);
        this.contractFunctionResult = contractCreateResult;
        return this;
    }

    /**
     * Gets the transferList.
     *
     * @return transferList
     */
    @Override
    @NonNull
    public TransferList transferList() {
        return transferList;
    }

    /**
     * Sets the transferList.
     *
     * @param transferList the transferList
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl transferList(@Nullable final TransferList transferList) {
        this.transferList = transferList;
        return this;
    }

    /**
     * Sets the tokenTransferLists.
     *
     * @param tokenTransferLists the tokenTransferLists
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl tokenTransferLists(
            @NonNull final List<TokenTransferList> tokenTransferLists) {
        requireNonNull(tokenTransferLists, "tokenTransferLists must not be null");
        this.tokenTransferLists = tokenTransferLists;
        return this;
    }

    @Override
    public List<TokenTransferList> tokenTransferLists() {
        return tokenTransferLists;
    }

    /**
     * Adds a tokenTransferList.
     *
     * @param tokenTransferList the tokenTransferList
     * @return the builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl addTokenTransferList(@NonNull final TokenTransferList tokenTransferList) {
        requireNonNull(tokenTransferList, "tokenTransferList must not be null");
        tokenTransferLists.add(tokenTransferList);
        return this;
    }

    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl tokenType(final @NonNull TokenType tokenType) {
        this.tokenType = requireNonNull(tokenType);
        return this;
    }

    /**
     * Sets the scheduleRef.
     *
     * @param scheduleRef the scheduleRef
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleRef(@NonNull final ScheduleID scheduleRef) {
        requireNonNull(scheduleRef, "scheduleRef must not be null");
        transactionRecordBuilder.scheduleRef(scheduleRef);
        return this;
    }

    /**
     * Sets the assessedCustomFees.
     *
     * @param assessedCustomFees the assessedCustomFees
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl assessedCustomFees(
            @NonNull final List<AssessedCustomFee> assessedCustomFees) {
        requireNonNull(assessedCustomFees, "assessedCustomFees must not be null");
        this.assessedCustomFees = assessedCustomFees;
        return this;
    }

    /**
     * Adds an assessedCustomFee.
     *
     * @param assessedCustomFee the assessedCustomFee
     * @return the builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl addAssessedCustomFee(@NonNull final AssessedCustomFee assessedCustomFee) {
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
    public SingleTransactionRecordBuilderImpl automaticTokenAssociations(
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
    public SingleTransactionRecordBuilderImpl addAutomaticTokenAssociation(
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
    public SingleTransactionRecordBuilderImpl alias(@NonNull final Bytes alias) {
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
    public SingleTransactionRecordBuilderImpl ethereumHash(@NonNull final Bytes ethereumHash) {
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
    public SingleTransactionRecordBuilderImpl paidStakingRewards(
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
    public SingleTransactionRecordBuilderImpl addPaidStakingReward(@NonNull final AccountAmount paidStakingReward) {
        requireNonNull(paidStakingReward, "paidStakingReward must not be null");
        paidStakingRewards.add(paidStakingReward);
        return this;
    }

    /**
     * Sets the entropy to a given number.
     *
     * @param num number to use for entropy
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl entropyNumber(final int num) {
        transactionRecordBuilder.prngNumber(num);
        return this;
    }

    /**
     * Sets the entropy to given bytes.
     *
     * @param prngBytes bytes to use for entropy
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes, "The argument 'prngBytes' must not be null");
        transactionRecordBuilder.prngBytes(prngBytes);
        return this;
    }

    /**
     * Sets the EVM address.
     *
     * @param evmAddress the EVM address
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl evmAddress(@NonNull final Bytes evmAddress) {
        requireNonNull(evmAddress, "evmAddress must not be null");
        transactionRecordBuilder.evmAddress(evmAddress);
        return this;
    }

    @Override
    public @NonNull List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
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
    public SingleTransactionRecordBuilderImpl status(@NonNull final ResponseCodeEnum status) {
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
     * Sets the receipt accountID.
     *
     * @param accountID the {@link AccountID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl accountID(@NonNull final AccountID accountID) {
        requireNonNull(accountID, "accountID must not be null");
        transactionReceiptBuilder.accountID(accountID);
        return this;
    }

    /**
     * Sets the receipt fileID.
     *
     * @param fileID the {@link FileID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl fileID(@NonNull final FileID fileID) {
        requireNonNull(fileID, "fileID must not be null");
        transactionReceiptBuilder.fileID(fileID);
        return this;
    }

    /**
     * Sets the receipt contractID; if the contractID is null, this is a no-op. (We allow a null id here
     * for convenience when chaining builder calls.)
     *
     * @param contractID the {@link ContractID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl contractID(@Nullable final ContractID contractID) {
        // Ensure we don't externalize as an account creation too
        transactionReceiptBuilder.accountID((AccountID) null);
        transactionReceiptBuilder.contractID(contractID);
        return this;
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
    public SingleTransactionRecordBuilderImpl exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
        requireNonNull(exchangeRate, "exchangeRate must not be null");
        this.exchangeRate = exchangeRate;
        return this;
    }

    /**
     * Sets the receipt topicID.
     *
     * @param topicID the {@link TopicID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl topicID(@NonNull final TopicID topicID) {
        requireNonNull(topicID, "topicID must not be null");
        transactionReceiptBuilder.topicID(topicID);
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
    public SingleTransactionRecordBuilderImpl topicSequenceNumber(final long topicSequenceNumber) {
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
    public SingleTransactionRecordBuilderImpl topicRunningHash(@NonNull final Bytes topicRunningHash) {
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
    public SingleTransactionRecordBuilderImpl topicRunningHashVersion(final long topicRunningHashVersion) {
        transactionReceiptBuilder.topicRunningHashVersion(topicRunningHashVersion);
        return this;
    }

    /**
     * Sets the receipt tokenID.
     *
     * @param tokenID the {@link TokenID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl tokenID(@NonNull final TokenID tokenID) {
        requireNonNull(tokenID, "tokenID must not be null");
        this.tokenID = tokenID;
        transactionReceiptBuilder.tokenID(tokenID);
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
    public SingleTransactionRecordBuilderImpl newTotalSupply(final long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        transactionReceiptBuilder.newTotalSupply(newTotalSupply);
        return this;
    }

    public long getNewTotalSupply() {
        return newTotalSupply;
    }

    /**
     * Sets the receipt scheduleID.
     *
     * @param scheduleID the {@link ScheduleID} for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl scheduleID(@NonNull final ScheduleID scheduleID) {
        requireNonNull(scheduleID, "scheduleID must not be null");
        transactionReceiptBuilder.scheduleID(scheduleID);
        return this;
    }

    /**
     * Sets the transaction ID of the scheduled child transaction that was executed
     *
     * @param scheduledTransactionID the {@link TransactionID} of the scheduled transaction for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl scheduledTransactionID(
            @NonNull final TransactionID scheduledTransactionID) {
        transactionReceiptBuilder.scheduledTransactionID(scheduledTransactionID);
        return this;
    }

    /**
     * Sets the receipt serialNumbers.
     *
     * @param serialNumbers the serialNumbers for the receipt
     * @return the builder
     */
    @Override
    @NonNull
    public SingleTransactionRecordBuilderImpl serialNumbers(@NonNull final List<Long> serialNumbers) {
        requireNonNull(serialNumbers, "serialNumbers must not be null");
        this.serialNumbers = serialNumbers;
        return this;
    }

    @Override
    public List<Long> serialNumbers() {
        return serialNumbers;
    }

    /**
     * Adds a serialNumber to the receipt.
     *
     * @param serialNumber the serialNumber to add
     * @return the builder
     */
    @NonNull
    public SingleTransactionRecordBuilderImpl addSerialNumber(final long serialNumber) {
        serialNumbers.add(serialNumber);
        return this;
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
    public SingleTransactionRecordBuilderImpl contractStateChanges(
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
    public SingleTransactionRecordBuilderImpl addContractStateChanges(
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
    public SingleTransactionRecordBuilderImpl contractActions(
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
    public SingleTransactionRecordBuilderImpl addContractActions(
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
    public SingleTransactionRecordBuilderImpl contractBytecodes(
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
    public SingleTransactionRecordBuilderImpl addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        requireNonNull(contractBytecode, "contractBytecode must not be null");
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }

    // ------------- Information needed by token service for redirecting staking rewards to appropriate accounts

    /**
     * Adds a beneficiary for a deleted account into the map. This is needed while computing staking rewards.
     * If the deleted account receives staking reward, it is transferred to the beneficiary.
     *
     * @param deletedAccountID the deleted account ID
     * @param beneficiaryForDeletedAccount the beneficiary account ID
     */
    @Override
    @NonNull
    public void addBeneficiaryForDeletedAccount(
            @NonNull final AccountID deletedAccountID, @NonNull final AccountID beneficiaryForDeletedAccount) {
        requireNonNull(deletedAccountID, "deletedAccountID must not be null");
        requireNonNull(beneficiaryForDeletedAccount, "beneficiaryForDeletedAccount must not be null");
        deletedAccountBeneficiaries.put(deletedAccountID, beneficiaryForDeletedAccount);
    }

    /**
     * Gets number of deleted accounts in this transaction.
     *
     * @return number of deleted accounts in this transaction
     */
    @Override
    public int getNumberOfDeletedAccounts() {
        return deletedAccountBeneficiaries.size();
    }

    /**
     * Gets the beneficiary account ID for deleted account ID.
     *
     * @return the beneficiary account ID of deleted account ID
     */
    @Override
    @Nullable
    public AccountID getDeletedAccountBeneficiaryFor(@NonNull final AccountID deletedAccountID) {
        return deletedAccountBeneficiaries.get(deletedAccountID);
    }

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

    public EthereumTransactionRecordBuilder feeChargedToPayer(@NonNull long amount) {
        transactionRecordBuilder.transactionFee(transactionFee + amount);
        return this;
    }

    /**
     * Returns the staking rewards paid in this transaction.
     *
     * @return the staking rewards paid in this transaction
     */
    public List<AccountAmount> getPaidStakingRewards() {
        return paidStakingRewards;
    }
}
