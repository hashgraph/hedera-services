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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.service.addressbook.impl.records.NodeCreateRecordBuilder;
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
import com.hedera.node.app.service.token.records.TokenBaseRecordBuilder;
import com.hedera.node.app.service.token.records.TokenBurnRecordBuilder;
import com.hedera.node.app.service.token.records.TokenCreateRecordBuilder;
import com.hedera.node.app.service.token.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.token.records.TokenUpdateRecordBuilder;
import com.hedera.node.app.service.util.impl.records.PrngRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.record.RecordBuilderImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * A temporary implementation of {@link StreamBuilder} that forwards all mutating calls to an
 * {@link IoBlockItemsBuilder} and a {@link RecordBuilderImpl}.
 * <p>
 */
public class PairedStreamBuilder
        implements StreamBuilder,
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
                CryptoUpdateRecordBuilder,
                NodeCreateRecordBuilder {
    private final IoBlockItemsBuilder ioBlockItemsBuilder;
    private final RecordBuilderImpl recordBuilder;

    public PairedStreamBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        recordBuilder = new RecordBuilderImpl(reversingBehavior, customizer, category);
        ioBlockItemsBuilder = new IoBlockItemsBuilder(reversingBehavior, customizer, category);
    }

    @Override
    public StreamBuilder stateChanges(@NonNull List<StateChange> stateChanges) {
        ioBlockItemsBuilder.stateChanges(stateChanges);
        return this;
    }

    public IoBlockItemsBuilder ioBlockItemsBuilder() {
        return ioBlockItemsBuilder;
    }

    public RecordBuilderImpl recordBuilder() {
        return recordBuilder;
    }

    @Override
    public PairedStreamBuilder transaction(@NonNull Transaction transaction) {
        recordBuilder.transaction(transaction);
        ioBlockItemsBuilder.transaction(transaction);
        return this;
    }

    @Override
    public Transaction transaction() {
        return recordBuilder.transaction();
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
        ioBlockItemsBuilder.nullOutSideEffectFields();
    }

    @Override
    public StreamBuilder syncBodyIdFromRecordId() {
        recordBuilder.syncBodyIdFromRecordId();
        ioBlockItemsBuilder.syncBodyIdFromRecordId();
        return this;
    }

    @Override
    public StreamBuilder consensusTimestamp(@NonNull final Instant now) {
        recordBuilder.consensusTimestamp(now);
        ioBlockItemsBuilder.consensusTimestamp(now);
        return this;
    }

    @Override
    public TransactionID transactionID() {
        return recordBuilder.transactionID();
    }

    @Override
    public StreamBuilder transactionID(@NonNull final TransactionID transactionID) {
        recordBuilder.transactionID(transactionID);
        ioBlockItemsBuilder.transactionID(transactionID);
        return this;
    }

    @Override
    public StreamBuilder parentConsensus(@NonNull final Instant parentConsensus) {
        recordBuilder.parentConsensus(parentConsensus);
        ioBlockItemsBuilder.parentConsensus(parentConsensus);
        return this;
    }

    @Override
    public StreamBuilder transactionBytes(@NonNull final Bytes transactionBytes) {
        recordBuilder.transactionBytes(transactionBytes);
        ioBlockItemsBuilder.transactionBytes(transactionBytes);
        return this;
    }

    @Override
    public StreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
        recordBuilder.exchangeRate(exchangeRate);
        ioBlockItemsBuilder.exchangeRate(exchangeRate);
        return this;
    }

    @Override
    public StreamBuilder congestionMultiplier(final long congestionMultiplier) {
        return null;
    }

    @NonNull
    @Override
    public NodeCreateRecordBuilder nodeID(long nodeID) {
        recordBuilder.nodeID(nodeID);
        ioBlockItemsBuilder.nodeID(nodeID);
        return this;
    }

    @NonNull
    @Override
    public ConsensusCreateTopicRecordBuilder topicID(@NonNull TopicID topicID) {
        recordBuilder.topicID(topicID);
        ioBlockItemsBuilder.topicID(topicID);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageRecordBuilder topicSequenceNumber(long topicSequenceNumber) {
        recordBuilder.topicSequenceNumber(topicSequenceNumber);
        ioBlockItemsBuilder.topicSequenceNumber(topicSequenceNumber);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageRecordBuilder topicRunningHash(@NonNull Bytes topicRunningHash) {
        recordBuilder.topicRunningHash(topicRunningHash);
        ioBlockItemsBuilder.topicRunningHash(topicRunningHash);
        return this;
    }

    @NonNull
    @Override
    public ConsensusSubmitMessageRecordBuilder topicRunningHashVersion(long topicRunningHashVersion) {
        recordBuilder.topicRunningHashVersion(topicRunningHashVersion);
        ioBlockItemsBuilder.topicRunningHashVersion(topicRunningHashVersion);
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
        ioBlockItemsBuilder.status(status);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractID(@Nullable ContractID contractId) {
        recordBuilder.contractID(contractId);
        ioBlockItemsBuilder.contractID(contractId);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractCreateResult(@Nullable ContractFunctionResult result) {
        recordBuilder.contractCreateResult(result);
        ioBlockItemsBuilder.contractCreateResult(result);
        return this;
    }

    @NonNull
    @Override
    public EthereumTransactionRecordBuilder ethereumHash(@NonNull Bytes ethereumHash) {
        recordBuilder.ethereumHash(ethereumHash);
        ioBlockItemsBuilder.ethereumHash(ethereumHash);
        return this;
    }

    @Override
    public void trackExplicitRewardSituation(@NonNull AccountID accountId) {
        recordBuilder.trackExplicitRewardSituation(accountId);
        ioBlockItemsBuilder.trackExplicitRewardSituation(accountId);
    }

    @NonNull
    @Override
    public ContractOperationRecordBuilder addContractActions(
            @NonNull ContractActions contractActions, boolean isMigration) {
        recordBuilder.addContractActions(contractActions, isMigration);
        ioBlockItemsBuilder.addContractActions(contractActions, isMigration);
        return this;
    }

    @NonNull
    @Override
    public ContractOperationRecordBuilder addContractBytecode(
            @NonNull ContractBytecode contractBytecode, boolean isMigration) {
        recordBuilder.addContractBytecode(contractBytecode, isMigration);
        ioBlockItemsBuilder.addContractBytecode(contractBytecode, isMigration);
        return this;
    }

    @NonNull
    @Override
    public ContractOperationRecordBuilder addContractStateChanges(
            @NonNull ContractStateChanges contractStateChanges, boolean isMigration) {
        recordBuilder.addContractStateChanges(contractStateChanges, isMigration);
        ioBlockItemsBuilder.addContractStateChanges(contractStateChanges, isMigration);
        return this;
    }

    @NonNull
    @Override
    public CreateFileRecordBuilder fileID(@NonNull FileID fileID) {
        recordBuilder.fileID(fileID);
        ioBlockItemsBuilder.fileID(fileID);
        return this;
    }

    @NonNull
    @Override
    public ScheduleRecordBuilder scheduleRef(ScheduleID scheduleRef) {
        recordBuilder.scheduleRef(scheduleRef);
        ioBlockItemsBuilder.scheduleRef(scheduleRef);
        return this;
    }

    @NonNull
    @Override
    public ScheduleRecordBuilder scheduleID(ScheduleID scheduleID) {
        recordBuilder.scheduleID(scheduleID);
        ioBlockItemsBuilder.scheduleID(scheduleID);
        return this;
    }

    @NonNull
    @Override
    public ScheduleRecordBuilder scheduledTransactionID(TransactionID scheduledTransactionID) {
        recordBuilder.scheduledTransactionID(scheduledTransactionID);
        ioBlockItemsBuilder.scheduledTransactionID(scheduledTransactionID);
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
        ioBlockItemsBuilder.accountID(accountID);
        return this;
    }

    @NonNull
    @Override
    public CryptoCreateRecordBuilder evmAddress(@NonNull Bytes evmAddress) {
        recordBuilder.evmAddress(evmAddress);
        ioBlockItemsBuilder.evmAddress(evmAddress);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder transactionFee(@NonNull long transactionFee) {
        recordBuilder.transactionFee(transactionFee);
        ioBlockItemsBuilder.transactionFee(transactionFee);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder memo(@NonNull String memo) {
        recordBuilder.memo(memo);
        ioBlockItemsBuilder.memo(memo);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferRecordBuilder transferList(@NonNull TransferList hbarTransfers) {
        recordBuilder.transferList(hbarTransfers);
        ioBlockItemsBuilder.transferList(hbarTransfers);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferRecordBuilder tokenTransferLists(@NonNull List<TokenTransferList> tokenTransferLists) {
        recordBuilder.tokenTransferLists(tokenTransferLists);
        ioBlockItemsBuilder.tokenTransferLists(tokenTransferLists);
        return this;
    }

    @NonNull
    @Override
    public CryptoTransferRecordBuilder assessedCustomFees(@NonNull List<AssessedCustomFee> assessedCustomFees) {
        recordBuilder.assessedCustomFees(assessedCustomFees);
        ioBlockItemsBuilder.assessedCustomFees(assessedCustomFees);
        return this;
    }

    @Override
    public CryptoTransferRecordBuilder paidStakingRewards(@NonNull List<AccountAmount> paidStakingRewards) {
        recordBuilder.paidStakingRewards(paidStakingRewards);
        ioBlockItemsBuilder.paidStakingRewards(paidStakingRewards);
        return this;
    }

    @Override
    public PairedStreamBuilder addAutomaticTokenAssociation(@NonNull TokenAssociation tokenAssociation) {
        recordBuilder.addAutomaticTokenAssociation(tokenAssociation);
        ioBlockItemsBuilder.addAutomaticTokenAssociation(tokenAssociation);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder contractCallResult(@Nullable ContractFunctionResult result) {
        recordBuilder.contractCallResult(result);
        ioBlockItemsBuilder.contractCallResult(result);
        return this;
    }

    @NonNull
    @Override
    public TokenCreateRecordBuilder tokenID(@NonNull TokenID tokenID) {
        recordBuilder.tokenID(tokenID);
        ioBlockItemsBuilder.tokenID(tokenID);
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
        ioBlockItemsBuilder.serialNumbers(serialNumbers);
        return this;
    }

    @Override
    public PairedStreamBuilder newTotalSupply(long newTotalSupply) {
        recordBuilder.newTotalSupply(newTotalSupply);
        ioBlockItemsBuilder.newTotalSupply(newTotalSupply);
        return this;
    }

    @Override
    public long getNewTotalSupply() {
        return recordBuilder.getNewTotalSupply();
    }

    @Override
    public TokenBaseRecordBuilder tokenType(@NonNull TokenType tokenType) {
        recordBuilder.tokenType(tokenType);
        ioBlockItemsBuilder.tokenType(tokenType);
        return this;
    }

    @NonNull
    @Override
    public PrngRecordBuilder entropyNumber(int num) {
        recordBuilder.entropyNumber(num);
        ioBlockItemsBuilder.entropyNumber(num);
        return this;
    }

    @NonNull
    @Override
    public PairedStreamBuilder entropyBytes(@NonNull Bytes prngBytes) {
        recordBuilder.entropyBytes(prngBytes);
        ioBlockItemsBuilder.entropyBytes(prngBytes);
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
        ioBlockItemsBuilder.addBeneficiaryForDeletedAccount(deletedAccountID, beneficiaryForDeletedAccount);
    }
}
