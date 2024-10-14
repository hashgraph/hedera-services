/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.blocks.impl;

import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_AIRDROP;
import static com.hedera.hapi.util.HapiUtils.asTimestamp;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.CallContractOutput;
import com.hedera.hapi.block.stream.output.CreateContractOutput;
import com.hedera.hapi.block.stream.output.CreateScheduleOutput;
import com.hedera.hapi.block.stream.output.CryptoTransferOutput;
import com.hedera.hapi.block.stream.output.EthereumOutput;
import com.hedera.hapi.block.stream.output.SignScheduleOutput;
import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TokenAirdropOutput;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.block.stream.output.UtilPrngOutput;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.HederaFunctionality;
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
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.platform.event.EventTransaction;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.hapi.streams.TransactionSidecarRecord;
import com.hedera.node.app.service.addressbook.impl.records.NodeCreateStreamBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusCreateTopicStreamBuilder;
import com.hedera.node.app.service.consensus.impl.records.ConsensusSubmitMessageStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractCreateStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractDeleteStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.ContractUpdateStreamBuilder;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionStreamBuilder;
import com.hedera.node.app.service.file.impl.records.CreateFileStreamBuilder;
import com.hedera.node.app.service.schedule.ScheduleStreamBuilder;
import com.hedera.node.app.service.token.api.FeeStreamBuilder;
import com.hedera.node.app.service.token.records.ChildStreamBuilder;
import com.hedera.node.app.service.token.records.CryptoCreateStreamBuilder;
import com.hedera.node.app.service.token.records.CryptoDeleteStreamBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.service.token.records.CryptoUpdateStreamBuilder;
import com.hedera.node.app.service.token.records.GenesisAccountStreamBuilder;
import com.hedera.node.app.service.token.records.NodeStakeUpdateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenAccountWipeStreamBuilder;
import com.hedera.node.app.service.token.records.TokenAirdropStreamBuilder;
import com.hedera.node.app.service.token.records.TokenBurnStreamBuilder;
import com.hedera.node.app.service.token.records.TokenCreateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenMintStreamBuilder;
import com.hedera.node.app.service.token.records.TokenUpdateStreamBuilder;
import com.hedera.node.app.service.util.impl.records.PrngStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
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
 * An implementation of {@link BlockStreamBuilder} that produces block items for a single user or
 * synthetic transaction; that is, the "input" block item with a {@link Transaction} and "output" block items
 * with a {@link TransactionResult} and, optionally, {@link TransactionOutput}.
 */
public class BlockStreamBuilder
        implements StreamBuilder,
                ConsensusCreateTopicStreamBuilder,
                ConsensusSubmitMessageStreamBuilder,
                CreateFileStreamBuilder,
                CryptoCreateStreamBuilder,
                CryptoTransferStreamBuilder,
                ChildStreamBuilder,
                PrngStreamBuilder,
                ScheduleStreamBuilder,
                TokenMintStreamBuilder,
                TokenBurnStreamBuilder,
                TokenCreateStreamBuilder,
                ContractCreateStreamBuilder,
                ContractCallStreamBuilder,
                ContractUpdateStreamBuilder,
                EthereumTransactionStreamBuilder,
                CryptoDeleteStreamBuilder,
                TokenUpdateStreamBuilder,
                NodeStakeUpdateStreamBuilder,
                FeeStreamBuilder,
                ContractDeleteStreamBuilder,
                GenesisAccountStreamBuilder,
                ContractOperationStreamBuilder,
                TokenAccountWipeStreamBuilder,
                CryptoUpdateStreamBuilder,
                NodeCreateStreamBuilder,
                TokenAirdropStreamBuilder {
    private static final Comparator<TokenAssociation> TOKEN_ASSOCIATION_COMPARATOR =
            Comparator.<TokenAssociation>comparingLong(a -> a.tokenIdOrThrow().tokenNum())
                    .thenComparingLong(a -> a.accountIdOrThrow().accountNumOrThrow());

    // base transaction data
    private Transaction transaction;

    @Nullable
    private Bytes serializedTransaction;

    // fields needed for TransactionRecord
    // Mutable because the provisional consensus timestamp assigned on dispatch could
    // change when removable records appear "between" this record and the parent record
    private Instant consensusNow;
    private TransactionID transactionID;
    private List<TokenTransferList> tokenTransferLists = new LinkedList<>();
    private boolean hasAssessedCustomFees = false;
    private List<AssessedCustomFee> assessedCustomFees = new LinkedList<>();

    private List<AccountAmount> paidStakingRewards = new LinkedList<>();
    private TransferList transferList = TransferList.DEFAULT;

    // fields needed for TransactionReceipt
    private ResponseCodeEnum status = ResponseCodeEnum.OK;
    private List<Long> serialNumbers = new LinkedList<>();
    private long newTotalSupply = 0L;
    // A set of ids that should be explicitly considered as in a "reward situation",
    // despite the canonical definition of a reward situation; needed for mono-service
    // fidelity only
    @Nullable
    private Set<AccountID> explicitRewardReceiverIds;
    // While the fee is sent to the underlying builder all the time, it is also cached here because, as of today,
    // there is no way to get the transaction fee from the PBJ object.
    private long transactionFee;
    // If non-null, a contract function result
    private ContractFunctionResult contractFunctionResult;
    // If non-null, the output of a UTIL_PRNG
    private BlockItem utilPrngOutputItem;

    private enum ContractOpType {
        CREATE,
        CALL,
        ETH_TBD,
        ETH_CREATE,
        ETH_CALL,
    }
    // The type of contract operation that was performed
    private ContractOpType contractOpType = null;
    private Bytes ethereumHash = Bytes.EMPTY;

    private final List<TokenAssociation> automaticTokenAssociations = new LinkedList<>();
    private final TransactionResult.Builder transactionResultBuilder = TransactionResult.newBuilder();
    // Sidecar data, booleans are the migration flag
    private final List<AbstractMap.SimpleEntry<ContractStateChanges, Boolean>> contractStateChanges =
            new LinkedList<>();
    private final List<AbstractMap.SimpleEntry<ContractActions, Boolean>> contractActions = new LinkedList<>();
    private final List<AbstractMap.SimpleEntry<ContractBytecode, Boolean>> contractBytecodes = new LinkedList<>();
    // Fields that are not in TransactionRecord, but are needed for computing staking rewards
    // These are not persisted to the record file
    private final Map<AccountID, AccountID> deletedAccountBeneficiaries = new HashMap<>();

    @Nullable
    private HederaFunctionality functionality;

    // Used for some child records builders.
    private final ReversingBehavior reversingBehavior;

    // Category of the record
    private final HandleContext.TransactionCategory category;

    private final List<StateChange> stateChanges = new ArrayList<>();

    // Used to customize the externalized form of a dispatched child transaction, right before
    // its record stream item is built; lets the contract service externalize certain dispatched
    // CryptoCreate transactions as ContractCreate synthetic transactions
    private final ExternalizedRecordCustomizer customizer;

    private TokenID tokenID;
    private ScheduleID scheduleID;
    private boolean createsOrDeletesSchedule;
    private TransactionID scheduledTransactionId;

    public BlockStreamBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        this.reversingBehavior = requireNonNull(reversingBehavior);
        this.customizer = requireNonNull(customizer);
        this.category = requireNonNull(category);
    }

    @Override
    public StreamBuilder stateChanges(@NonNull List<StateChange> stateChanges) {
        this.stateChanges.addAll(stateChanges);
        return this;
    }

    @Override
    public BlockStreamBuilder functionality(@NonNull final HederaFunctionality functionality) {
        this.functionality = requireNonNull(functionality);
        return this;
    }

    /**
     * Builds the list of block items.
     *
     * @return the list of block items
     */
    public List<BlockItem> build() {
        final var blockItems = new ArrayList<BlockItem>();

        final var transactionBlockItem = BlockItem.newBuilder()
                .eventTransaction(EventTransaction.newBuilder()
                        .applicationTransaction(getSerializedTransaction())
                        .build())
                .build();
        blockItems.add(transactionBlockItem);

        final var resultBlockItem = getTransactionResultBlockItem();
        blockItems.add(resultBlockItem);

        addOutputItems(blockItems);

        if (!stateChanges.isEmpty()) {
            final var stateChangesBlockItem = BlockItem.newBuilder()
                    .stateChanges(StateChanges.newBuilder()
                            .consensusTimestamp(asTimestamp(consensusNow))
                            .stateChanges(stateChanges)
                            .build())
                    .build();
            blockItems.add(stateChangesBlockItem);
        }
        return blockItems;
    }

    @Override
    @NonNull
    public ReversingBehavior reversingBehavior() {
        return reversingBehavior;
    }

    @Override
    public int getNumAutoAssociations() {
        return automaticTokenAssociations.size();
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // base transaction data

    @Override
    @NonNull
    public BlockStreamBuilder parentConsensus(@NonNull final Instant parentConsensus) {
        transactionResultBuilder.parentConsensusTimestamp(Timestamp.newBuilder()
                .seconds(parentConsensus.getEpochSecond())
                .nanos(parentConsensus.getNano())
                .build());
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder consensusTimestamp(@NonNull final Instant now) {
        this.consensusNow = requireNonNull(now, "consensus time must not be null");
        transactionResultBuilder.consensusTimestamp(Timestamp.newBuilder()
                .seconds(now.getEpochSecond())
                .nanos(now.getNano())
                .build());
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder transaction(@NonNull final Transaction transaction) {
        this.transaction = requireNonNull(transaction, "transaction must not be null");
        return this;
    }

    @Override
    public StreamBuilder serializedTransaction(@Nullable final Bytes serializedTransaction) {
        this.serializedTransaction = serializedTransaction;
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder transactionBytes(@NonNull final Bytes transactionBytes) {
        return this;
    }

    @Override
    @NonNull
    public TransactionID transactionID() {
        return transactionID;
    }

    @Override
    @NonNull
    public BlockStreamBuilder transactionID(@NonNull final TransactionID transactionID) {
        this.transactionID = requireNonNull(transactionID, "transactionID must not be null");
        return this;
    }

    @NonNull
    @Override
    public BlockStreamBuilder syncBodyIdFromRecordId() {
        final var newTransactionID = transactionID;
        final var body =
                inProgressBody().copyBuilder().transactionID(newTransactionID).build();
        this.transaction = StreamBuilder.transactionWith(body);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder memo(@NonNull final String memo) {
        // No-op
        return this;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionRecord

    @Override
    @NonNull
    public Transaction transaction() {
        return transaction;
    }

    @Override
    public long transactionFee() {
        return transactionFee;
    }

    @NonNull
    @Override
    public BlockStreamBuilder transactionFee(final long transactionFee) {
        transactionResultBuilder.transactionFeeCharged(transactionFee);
        this.transactionFee = transactionFee;
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

    @Override
    @NonNull
    public BlockStreamBuilder contractCallResult(@Nullable final ContractFunctionResult contractCallResult) {
        this.contractFunctionResult = contractCallResult;
        if (contractCallResult != null) {
            if (contractOpType != ContractOpType.ETH_TBD) {
                contractOpType = ContractOpType.CALL;
            } else {
                contractOpType = ContractOpType.ETH_CALL;
            }
        }
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder contractCreateResult(@Nullable ContractFunctionResult contractCreateResult) {
        this.contractFunctionResult = contractCreateResult;
        if (contractCreateResult != null) {
            if (contractOpType != ContractOpType.ETH_TBD) {
                contractOpType = ContractOpType.CREATE;
            } else {
                contractOpType = ContractOpType.ETH_CREATE;
            }
        }
        return this;
    }

    @Override
    @NonNull
    public TransferList transferList() {
        return transferList;
    }

    @Override
    @NonNull
    public BlockStreamBuilder transferList(@Nullable final TransferList transferList) {
        this.transferList = transferList;
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder tokenTransferLists(@NonNull final List<TokenTransferList> tokenTransferLists) {
        requireNonNull(tokenTransferLists, "tokenTransferLists must not be null");
        this.tokenTransferLists = tokenTransferLists;
        transactionResultBuilder.tokenTransferLists(tokenTransferLists);
        return this;
    }

    @Override
    public List<TokenTransferList> tokenTransferLists() {
        return tokenTransferLists;
    }

    @Override
    @NonNull
    public BlockStreamBuilder tokenType(final @NonNull TokenType tokenType) {
        return this;
    }

    @Override
    public BlockStreamBuilder addPendingAirdrop(@NonNull final PendingAirdropRecord pendingAirdropRecord) {
        requireNonNull(pendingAirdropRecord);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder scheduleRef(@NonNull final ScheduleID scheduleRef) {
        requireNonNull(scheduleRef, "scheduleRef must not be null");
        transactionResultBuilder.scheduleRef(scheduleRef);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder assessedCustomFees(@NonNull final List<AssessedCustomFee> assessedCustomFees) {
        this.assessedCustomFees = requireNonNull(assessedCustomFees);
        hasAssessedCustomFees = true;
        return this;
    }

    @NonNull
    public BlockStreamBuilder addAutomaticTokenAssociation(@NonNull final TokenAssociation automaticTokenAssociation) {
        requireNonNull(automaticTokenAssociation, "automaticTokenAssociation must not be null");
        automaticTokenAssociations.add(automaticTokenAssociation);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder ethereumHash(@NonNull final Bytes ethereumHash) {
        contractOpType = ContractOpType.ETH_TBD;
        this.ethereumHash = requireNonNull(ethereumHash);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder paidStakingRewards(@NonNull final List<AccountAmount> paidStakingRewards) {
        // These need not be externalized to block streams
        requireNonNull(paidStakingRewards, "paidStakingRewards must not be null");
        this.paidStakingRewards = paidStakingRewards;
        transactionResultBuilder.paidStakingRewards(paidStakingRewards);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder entropyNumber(final int num) {
        utilPrngOutputItem = itemWith(TransactionOutput.newBuilder()
                .utilPrng(UtilPrngOutput.newBuilder().prngNumber(num).build()));
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder entropyBytes(@NonNull final Bytes prngBytes) {
        requireNonNull(prngBytes);
        utilPrngOutputItem = itemWith(TransactionOutput.newBuilder()
                .utilPrng(UtilPrngOutput.newBuilder().prngBytes(prngBytes).build()));
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder evmAddress(@NonNull final Bytes evmAddress) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // fields needed for TransactionReceipt

    @Override
    @NonNull
    public BlockStreamBuilder status(@NonNull final ResponseCodeEnum status) {
        this.status = requireNonNull(status, "status must not be null");
        transactionResultBuilder.status(status);
        return this;
    }

    @Override
    @NonNull
    public ResponseCodeEnum status() {
        return status;
    }

    @Override
    public boolean hasContractResult() {
        return this.contractFunctionResult != null;
    }

    @Override
    public long getGasUsedForContractTxn() {
        return this.contractFunctionResult.gasUsed();
    }

    @Override
    @NonNull
    public BlockStreamBuilder accountID(@NonNull final AccountID accountID) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder fileID(@NonNull final FileID fileID) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder contractID(@Nullable final ContractID contractID) {
        // No-op
        return this;
    }

    @NonNull
    @Override
    public BlockStreamBuilder exchangeRate(@Nullable final ExchangeRateSet exchangeRate) {
        transactionResultBuilder.exchangeRate(exchangeRate);
        return this;
    }

    @NonNull
    @Override
    public BlockStreamBuilder congestionMultiplier(long congestionMultiplier) {
        if (congestionMultiplier != 0) {
            transactionResultBuilder.congestionPricingMultiplier(congestionMultiplier);
        }
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder topicID(@NonNull final TopicID topicID) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder topicSequenceNumber(final long topicSequenceNumber) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder topicRunningHash(@NonNull final Bytes topicRunningHash) {
        // No-op
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder topicRunningHashVersion(final long topicRunningHashVersion) {
        // TOD0: Need to confirm what the value should be
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder tokenID(@NonNull final TokenID tokenID) {
        requireNonNull(tokenID, "tokenID must not be null");
        this.tokenID = tokenID;
        return this;
    }

    @Override
    public TokenID tokenID() {
        return tokenID;
    }

    @Override
    @NonNull
    public BlockStreamBuilder nodeID(long nodeId) {
        // No-op
        return this;
    }

    @NonNull
    public BlockStreamBuilder newTotalSupply(final long newTotalSupply) {
        this.newTotalSupply = newTotalSupply;
        return this;
    }

    @Override
    public long getNewTotalSupply() {
        return newTotalSupply;
    }

    @Override
    @NonNull
    public BlockStreamBuilder scheduleID(@NonNull final ScheduleID scheduleID) {
        this.createsOrDeletesSchedule = true;
        this.scheduleID = requireNonNull(scheduleID);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder scheduledTransactionID(@NonNull final TransactionID scheduledTransactionID) {
        this.scheduledTransactionId = requireNonNull(scheduledTransactionID);
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder serialNumbers(@NonNull final List<Long> serialNumbers) {
        requireNonNull(serialNumbers, "serialNumbers must not be null");
        this.serialNumbers = serialNumbers;
        return this;
    }

    @Override
    @NonNull
    public List<Long> serialNumbers() {
        return serialNumbers;
    }

    // ------------------------------------------------------------------------------------------------------------------------
    // Sidecar data, booleans are the migration flag

    @Override
    @NonNull
    public BlockStreamBuilder addContractStateChanges(
            @NonNull final ContractStateChanges contractStateChanges, final boolean isMigration) {
        requireNonNull(contractStateChanges, "contractStateChanges must not be null");
        this.contractStateChanges.add(new AbstractMap.SimpleEntry<>(contractStateChanges, isMigration));
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder addContractActions(
            @NonNull final ContractActions contractActions, final boolean isMigration) {
        requireNonNull(contractActions, "contractActions must not be null");
        this.contractActions.add(new AbstractMap.SimpleEntry<>(contractActions, isMigration));
        return this;
    }

    @Override
    @NonNull
    public BlockStreamBuilder addContractBytecode(
            @NonNull final ContractBytecode contractBytecode, final boolean isMigration) {
        requireNonNull(contractBytecode, "contractBytecode must not be null");
        contractBytecodes.add(new AbstractMap.SimpleEntry<>(contractBytecode, isMigration));
        return this;
    }

    // ------------- Information needed by token service for redirecting staking rewards to appropriate accounts

    @Override
    public void addBeneficiaryForDeletedAccount(
            @NonNull final AccountID deletedAccountID, @NonNull final AccountID beneficiaryForDeletedAccount) {
        requireNonNull(deletedAccountID, "deletedAccountID must not be null");
        requireNonNull(beneficiaryForDeletedAccount, "beneficiaryForDeletedAccount must not be null");
        deletedAccountBeneficiaries.put(deletedAccountID, beneficiaryForDeletedAccount);
    }

    @Override
    public int getNumberOfDeletedAccounts() {
        return deletedAccountBeneficiaries.size();
    }

    @Override
    @Nullable
    public AccountID getDeletedAccountBeneficiaryFor(@NonNull final AccountID deletedAccountID) {
        return deletedAccountBeneficiaries.get(deletedAccountID);
    }

    @Override
    @NonNull
    public ContractFunctionResult contractFunctionResult() {
        return contractFunctionResult;
    }

    @Override
    @NonNull
    public TransactionBody transactionBody() {
        return inProgressBody();
    }

    @NonNull
    @Override
    public List<AccountAmount> getPaidStakingRewards() {
        return paidStakingRewards;
    }

    @Override
    @NonNull
    public HandleContext.TransactionCategory category() {
        return category;
    }

    @Override
    public void nullOutSideEffectFields() {
        serialNumbers.clear();
        tokenTransferLists.clear();
        automaticTokenAssociations.clear();
        transferList = TransferList.DEFAULT;
        paidStakingRewards.clear();
        assessedCustomFees.clear();

        newTotalSupply = 0L;
        transactionFee = 0L;
        transactionResultBuilder.scheduleRef((ScheduleID) null);
        transactionResultBuilder.automaticTokenAssociations(emptyList());
    }

    @NonNull
    private BlockItem getTransactionResultBlockItem() {
        if (!automaticTokenAssociations.isEmpty()) {
            automaticTokenAssociations.sort(TOKEN_ASSOCIATION_COMPARATOR);
            transactionResultBuilder.automaticTokenAssociations(automaticTokenAssociations);
        }
        return BlockItem.newBuilder()
                .transactionResult(
                        transactionResultBuilder.transferList(transferList).build())
                .build();
    }

    private TransactionBody inProgressBody() {
        try {
            final var signedTransaction = SignedTransaction.PROTOBUF.parseStrict(
                    transaction.signedTransactionBytes().toReadableSequentialData());
            return TransactionBody.PROTOBUF.parse(signedTransaction.bodyBytes().toReadableSequentialData());
        } catch (Exception e) {
            throw new IllegalStateException("Record being built for unparseable transaction", e);
        }
    }

    private void addOutputItems(@NonNull final List<BlockItem> items) {
        if (utilPrngOutputItem != null) {
            items.add(utilPrngOutputItem);
        }
        if (contractFunctionResult != null || ethereumHash != Bytes.EMPTY) {
            final var sidecars = getSidecars();
            final var builder = TransactionOutput.newBuilder();
            switch (requireNonNull(contractOpType)) {
                case CREATE -> builder.contractCreate(CreateContractOutput.newBuilder()
                        .contractCreateResult(contractFunctionResult)
                        .sidecars(sidecars)
                        .build());
                case CALL -> builder.contractCall(CallContractOutput.newBuilder()
                        .contractCallResult(contractFunctionResult)
                        .sidecars(sidecars)
                        .build());
                case ETH_CALL -> builder.ethereumCall(EthereumOutput.newBuilder()
                        .ethereumCallResult(contractFunctionResult)
                        .ethereumHash(ethereumHash)
                        .sidecars(sidecars)
                        .build());
                case ETH_CREATE -> builder.ethereumCall(EthereumOutput.newBuilder()
                        .ethereumCreateResult(contractFunctionResult)
                        .ethereumHash(ethereumHash)
                        .sidecars(sidecars)
                        .build());
                    // CONSENSUS_GAS_EXHAUSTED if there is no contract function result
                case ETH_TBD -> builder.ethereumCall(EthereumOutput.newBuilder()
                        .ethereumHash(ethereumHash)
                        .sidecars(sidecars)
                        .build());
            }
            items.add(itemWith(builder));
        }
        if (createsOrDeletesSchedule && scheduledTransactionId != null) {
            items.add(itemWith(TransactionOutput.newBuilder()
                    .createSchedule(CreateScheduleOutput.newBuilder()
                            .scheduleId(scheduleID)
                            .scheduledTransactionId(scheduledTransactionId)
                            .build())));
        } else if (scheduledTransactionId != null) {
            items.add(itemWith(TransactionOutput.newBuilder()
                    .signSchedule(SignScheduleOutput.newBuilder()
                            .scheduledTransactionId(scheduledTransactionId)
                            .build())));
        }
        if (functionality == CRYPTO_TRANSFER && hasAssessedCustomFees) {
            items.add(itemWith(TransactionOutput.newBuilder()
                    .cryptoTransfer(CryptoTransferOutput.newBuilder()
                            .assessedCustomFees(assessedCustomFees)
                            .build())));
        } else if (functionality == TOKEN_AIRDROP && hasAssessedCustomFees) {
            items.add(
                    itemWith(TransactionOutput.newBuilder().tokenAirdrop(new TokenAirdropOutput(assessedCustomFees))));
        }
    }

    private List<TransactionSidecarRecord> getSidecars() {
        final var timestamp = asTimestamp(consensusNow);
        // create list of sidecar records
        final List<TransactionSidecarRecord> transactionSidecarRecords = new ArrayList<>();
        contractStateChanges.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.STATE_CHANGES, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractActions.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.ACTIONS, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        contractBytecodes.stream()
                .map(pair -> new TransactionSidecarRecord(
                        timestamp,
                        pair.getValue(),
                        new OneOf<>(TransactionSidecarRecord.SidecarRecordsOneOfType.BYTECODE, pair.getKey())))
                .forEach(transactionSidecarRecords::add);
        return transactionSidecarRecords;
    }

    private Bytes getSerializedTransaction() {
        if (customizer != null) {
            transaction = customizer.apply(transaction);
            return Transaction.PROTOBUF.toBytes(transaction);
        }
        return serializedTransaction != null ? serializedTransaction : Transaction.PROTOBUF.toBytes(transaction);
    }

    private BlockItem itemWith(@NonNull final TransactionOutput.Builder output) {
        return BlockItem.newBuilder().transactionOutput(output).build();
    }
}
