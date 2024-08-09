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

import com.hedera.hapi.block.stream.output.StateChange;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ScheduleID;
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
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
import com.hedera.node.app.service.token.records.TokenBaseStreamBuilder;
import com.hedera.node.app.service.token.records.TokenBurnStreamBuilder;
import com.hedera.node.app.service.token.records.TokenCreateStreamBuilder;
import com.hedera.node.app.service.token.records.TokenMintStreamBuilder;
import com.hedera.node.app.service.token.records.TokenUpdateStreamBuilder;
import com.hedera.node.app.service.util.impl.records.PrngStreamBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A temporary implementation of {@link StreamBuilder} that forwards all mutating calls to an
 * {@link BlockStreamBuilder} and a {@link RecordStreamBuilder}.
 */
public class PairedStreamBuilder
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
    private final BlockStreamBuilder blockStreamBuilder;
    private final RecordStreamBuilder recordBuilder;

    public PairedStreamBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        recordBuilder = new RecordStreamBuilder(reversingBehavior, customizer, category);
        blockStreamBuilder = new BlockStreamBuilder(reversingBehavior, customizer, category);
    }

    @Override
    public StreamBuilder stateChanges(@NonNull List<StateChange> stateChanges) {
        blockStreamBuilder.stateChanges(stateChanges);
        return this;
    }

    public BlockStreamBuilder ioBlockItemsBuilder() {
        return blockStreamBuilder;
    }

    public RecordStreamBuilder recordBuilder() {
        return recordBuilder;
    }

    @Override
    public PairedStreamBuilder transaction(@NonNull Transaction transaction) {
        recordBuilder.transaction(transaction);
        blockStreamBuilder.transaction(transaction);
        return this;
    }

    @Override
    public PairedStreamBuilder serializedTransaction(@Nullable final Bytes serializedTransaction) {
        recordBuilder.serializedTransaction(serializedTransaction);
        blockStreamBuilder.serializedTransaction(serializedTransaction);
        return this;
    }

    @Override
    public int getNumAutoAssociations() {
        return blockStreamBuilder.getNumAutoAssociations();
    }

    @Override
    public Transaction transaction() {
        return recordBuilder.transaction();
    }

    @Override
    public TokenAirdropStreamBuilder addPendingAirdrop(@NonNull final PendingAirdropRecord pendingAirdropRecord) {
        recordBuilder.addPendingAirdrop(pendingAirdropRecord);
        blockStreamBuilder.addPendingAirdrop(pendingAirdropRecord);
        return this;
    }

    @Override
    public Set<AccountID> explicitRewardSituationIds() {
        return recordBuilder.explicitRewardSituationIds();
    }

    @Override
    public List<AccountAmount> getPaidStakingRewards() {
        return recordBuilder.getPaidStakingRewards();
    }

    @Override
    public boolean hasContractResult() {
        return recordBuilder.hasContractResult();
    }

    @Override
    public long getGasUsedForContractTxn() {
        return recordBuilder.getGasUsedForContractTxn();
    }

    @NonNull
    @Override
    public ResponseCodeEnum status() {
        return recordBuilder.status();
    }

    @NonNull
    @Override
    public TransactionBody transactionBody() {
        return recordBuilder.transactionBody();
    }

    @Override
    public long transactionFee() {
        return recordBuilder.transactionFee();
    }

    @Override
    public HandleContext.TransactionCategory category() {
        return recordBuilder.category();
    }

    @Override
    public ReversingBehavior reversingBehavior() {
        return recordBuilder.reversingBehavior();
    }

    @Override
    public void nullOutSideEffectFields() {
        recordBuilder.nullOutSideEffectFields();
        blockStreamBuilder.nullOutSideEffectFields();
    }

    @Override
    public StreamBuilder syncBodyIdFromRecordId() {
        recordBuilder.syncBodyIdFromRecordId();
        blockStreamBuilder.syncBodyIdFromRecordId();
        return this;
    }

    @Override
    public StreamBuilder consensusTimestamp(@NonNull final Instant now) {
        recordBuilder.consensusTimestamp(now);
        blockStreamBuilder.consensusTimestamp(now);
        return this;
    }

    @Override
    public TransactionID transactionID() {
        return recordBuilder.transactionID();
    }

    @Override
    public StreamBuilder transactionID(@NonNull final TransactionID transactionID) {
        recordBuilder.transactionID(transactionID);
        blockStreamBuilder.transactionID(transactionID);
        return this;
    }

    @Override
    public StreamBuilder parentConsensus(@NonNull final Instant parentConsensus) {
        recordBuilder.parentConsensus(parentConsensus);
        blockStreamBuilder.parentConsensus(parentConsensus);
        return this;
    }

    @Override
    public StreamBuilder transactionBytes(@NonNull final Bytes transactionBytes) {
        recordBuilder.transactionBytes(transactionBytes);
        blockStreamBuilder.transactionBytes(transactionBytes);
        return this;
    }

    @Override
    public StreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
        recordBuilder.exchangeRate(exchangeRate);
        blockStreamBuilder.exchangeRate(exchangeRate);
        return this;
    }

    @Override
    public StreamBuilder congestionMultiplier(final long congestionMultiplier) {
        return null;
    }

    @NonNull
    @Override
    public NodeCreateStreamBuilder nodeID(long nodeID) {
        recordBuilder.nodeID(nodeID);
        blockStreamBuilder.nodeID(nodeID);
        return this;
    }

    @NonNull
    @Override
    public ConsensusCreateTopicStreamBuilder topicID(@NonNull TopicID topicID) {
        recordBuilder.topicID(topicID);
        blockStreamBuilder.topicID(topicID);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageStreamBuilder topicSequenceNumber(long topicSequenceNumber) {
        recordBuilder.topicSequenceNumber(topicSequenceNumber);
        blockStreamBuilder.topicSequenceNumber(topicSequenceNumber);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageStreamBuilder topicRunningHash(@NonNull Bytes topicRunningHash) {
        recordBuilder.topicRunningHash(topicRunningHash);
        blockStreamBuilder.topicRunningHash(topicRunningHash);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageStreamBuilder topicRunningHashVersion(long topicRunningHashVersion) {
        recordBuilder.topicRunningHashVersion(topicRunningHashVersion);
        blockStreamBuilder.topicRunningHashVersion(topicRunningHashVersion);
        return this;
    }

    @NonNull
    @Override
    public List<AssessedCustomFee> getAssessedCustomFees() {
        return recordBuilder.getAssessedCustomFees();
    }

    @Override
    public ContractFunctionResult contractFunctionResult() {
        return recordBuilder.contractFunctionResult();
    }

    @Override
    public List<Long> serialNumbers() {
        return recordBuilder.serialNumbers();
    }

    @NonNull
    @Override
    public PairedStreamBuilder status(@NonNull ResponseCodeEnum status) {
        recordBuilder.status(status);
        blockStreamBuilder.status(status);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractID(@Nullable ContractID contractId) {
        recordBuilder.contractID(contractId);
        blockStreamBuilder.contractID(contractId);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractCreateResult(@Nullable ContractFunctionResult result) {
        recordBuilder.contractCreateResult(result);
        blockStreamBuilder.contractCreateResult(result);
        return this;
    }

    @NonNull
    @Override
    public EthereumTransactionStreamBuilder ethereumHash(@NonNull Bytes ethereumHash) {
        recordBuilder.ethereumHash(ethereumHash);
        blockStreamBuilder.ethereumHash(ethereumHash);
        return this;
    }

    @Override
    public void trackExplicitRewardSituation(@NonNull AccountID accountId) {
        recordBuilder.trackExplicitRewardSituation(accountId);
        blockStreamBuilder.trackExplicitRewardSituation(accountId);
    }

    @NonNull
    @Override
    public ContractOperationStreamBuilder addContractActions(
            @NonNull ContractActions contractActions, boolean isMigration) {
        recordBuilder.addContractActions(contractActions, isMigration);
        blockStreamBuilder.addContractActions(contractActions, isMigration);
        return this;
    }

    @NonNull
    @Override
    public ContractOperationStreamBuilder addContractBytecode(
            @NonNull ContractBytecode contractBytecode, boolean isMigration) {
        recordBuilder.addContractBytecode(contractBytecode, isMigration);
        blockStreamBuilder.addContractBytecode(contractBytecode, isMigration);
        return this;
    }

    @NonNull
    @Override
    public ContractOperationStreamBuilder addContractStateChanges(
            @NonNull ContractStateChanges contractStateChanges, boolean isMigration) {
        recordBuilder.addContractStateChanges(contractStateChanges, isMigration);
        blockStreamBuilder.addContractStateChanges(contractStateChanges, isMigration);
        return this;
    }

    @NonNull
    @Override
    public CreateFileStreamBuilder fileID(@NonNull FileID fileID) {
        recordBuilder.fileID(fileID);
        blockStreamBuilder.fileID(fileID);
        return this;
    }

    @NonNull
    @Override
    public ScheduleStreamBuilder scheduleRef(ScheduleID scheduleRef) {
        recordBuilder.scheduleRef(scheduleRef);
        blockStreamBuilder.scheduleRef(scheduleRef);
        return this;
    }

    @NonNull
    @Override
    public ScheduleStreamBuilder scheduleID(ScheduleID scheduleID) {
        recordBuilder.scheduleID(scheduleID);
        blockStreamBuilder.scheduleID(scheduleID);
        return this;
    }

    @NonNull
    @Override
    public ScheduleStreamBuilder scheduledTransactionID(TransactionID scheduledTransactionID) {
        recordBuilder.scheduledTransactionID(scheduledTransactionID);
        blockStreamBuilder.scheduledTransactionID(scheduledTransactionID);
        return this;
    }

    @Override
    public TransferList transferList() {
        return recordBuilder.transferList();
    }

    @Override
    public List<TokenTransferList> tokenTransferLists() {
        return recordBuilder.tokenTransferLists();
    }

    @NonNull
    @Override
    public PairedStreamBuilder accountID(@NonNull AccountID accountID) {
        recordBuilder.accountID(accountID);
        blockStreamBuilder.accountID(accountID);
        return this;
    }

    @NonNull
    @Override
    public CryptoCreateStreamBuilder evmAddress(@NonNull Bytes evmAddress) {
        recordBuilder.evmAddress(evmAddress);
        blockStreamBuilder.evmAddress(evmAddress);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder transactionFee(@NonNull long transactionFee) {
        recordBuilder.transactionFee(transactionFee);
        blockStreamBuilder.transactionFee(transactionFee);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder memo(@NonNull String memo) {
        recordBuilder.memo(memo);
        blockStreamBuilder.memo(memo);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferStreamBuilder transferList(@NonNull TransferList hbarTransfers) {
        recordBuilder.transferList(hbarTransfers);
        blockStreamBuilder.transferList(hbarTransfers);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferStreamBuilder tokenTransferLists(@NonNull List<TokenTransferList> tokenTransferLists) {
        recordBuilder.tokenTransferLists(tokenTransferLists);
        blockStreamBuilder.tokenTransferLists(tokenTransferLists);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferStreamBuilder assessedCustomFees(@NonNull List<AssessedCustomFee> assessedCustomFees) {
        recordBuilder.assessedCustomFees(assessedCustomFees);
        blockStreamBuilder.assessedCustomFees(assessedCustomFees);
        return this;
    }

    @Override
    public CryptoTransferStreamBuilder paidStakingRewards(@NonNull List<AccountAmount> paidStakingRewards) {
        recordBuilder.paidStakingRewards(paidStakingRewards);
        blockStreamBuilder.paidStakingRewards(paidStakingRewards);
        return this;
    }

    @Override
    public PairedStreamBuilder addAutomaticTokenAssociation(@NonNull TokenAssociation tokenAssociation) {
        recordBuilder.addAutomaticTokenAssociation(tokenAssociation);
        blockStreamBuilder.addAutomaticTokenAssociation(tokenAssociation);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractCallResult(@Nullable ContractFunctionResult result) {
        recordBuilder.contractCallResult(result);
        blockStreamBuilder.contractCallResult(result);
        return this;
    }

    @NonNull
    @Override
    public TokenCreateStreamBuilder tokenID(@NonNull TokenID tokenID) {
        recordBuilder.tokenID(tokenID);
        blockStreamBuilder.tokenID(tokenID);
        return this;
    }

    @Override
    public TokenID tokenID() {
        return recordBuilder.tokenID();
    }

    @NonNull
    @Override
    public PairedStreamBuilder serialNumbers(@NonNull List<Long> serialNumbers) {
        recordBuilder.serialNumbers(serialNumbers);
        blockStreamBuilder.serialNumbers(serialNumbers);
        return this;
    }

    @Override
    public PairedStreamBuilder newTotalSupply(long newTotalSupply) {
        recordBuilder.newTotalSupply(newTotalSupply);
        blockStreamBuilder.newTotalSupply(newTotalSupply);
        return this;
    }

    @Override
    public long getNewTotalSupply() {
        return recordBuilder.getNewTotalSupply();
    }

    @Override
    public TokenBaseStreamBuilder tokenType(@NonNull TokenType tokenType) {
        recordBuilder.tokenType(tokenType);
        blockStreamBuilder.tokenType(tokenType);
        return this;
    }

    @NonNull
    @Override
    public PrngStreamBuilder entropyNumber(int num) {
        recordBuilder.entropyNumber(num);
        blockStreamBuilder.entropyNumber(num);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder entropyBytes(@NonNull Bytes prngBytes) {
        recordBuilder.entropyBytes(prngBytes);
        blockStreamBuilder.entropyBytes(prngBytes);
        return this;
    }

    @Override
    public int getNumberOfDeletedAccounts() {
        return recordBuilder.getNumberOfDeletedAccounts();
    }

    @Nullable
    @Override
    public AccountID getDeletedAccountBeneficiaryFor(@NonNull AccountID deletedAccountID) {
        return recordBuilder.getDeletedAccountBeneficiaryFor(deletedAccountID);
    }

    @Override
    public void addBeneficiaryForDeletedAccount(
            @NonNull AccountID deletedAccountID, @NonNull AccountID beneficiaryForDeletedAccount) {
        recordBuilder.addBeneficiaryForDeletedAccount(deletedAccountID, beneficiaryForDeletedAccount);
        blockStreamBuilder.addBeneficiaryForDeletedAccount(deletedAccountID, beneficiaryForDeletedAccount);
    }
}
