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

package com.hedera.node.app.blocks;

import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.TransactionReceipt;
import com.hedera.hapi.node.transaction.TransactionRecord;
import com.hedera.hapi.streams.ContractActions;
import com.hedera.hapi.streams.ContractBytecode;
import com.hedera.hapi.streams.ContractStateChanges;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionStreamBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.time.Instant;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An implementation of {@link SingleTransactionStreamBuilder} that produces block items for a single user or
 * synthetic transaction; that is, the "input" block item with a {@link Transaction} and "output" block items
 * with a {@link TransactionResult} and, optionally, {@link TransactionOutput}.
 *
 */
public class IoBlockItemsBuilder implements SingleTransactionStreamBuilder {
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

    // Category of the record
    private final HandleContext.TransactionCategory category;

    // Used to customize the externalized form of a dispatched child transaction, right before
    // its record stream item is built; lets the contract service externalize certain dispatched
    // CryptoCreate transactions as ContractCreate synthetic transactions
    private final ExternalizedRecordCustomizer customizer;

    private TokenID tokenID;
    private TokenType tokenType;

    public IoBlockItemsBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        this.reversingBehavior = reversingBehavior;
        this.customizer = customizer;
        this.category = category;
    }

    /**
     * Streams the block items produced by this builder to the given manager.
     * @param manager the manager to which the block items are streamed
     */
    public void streamTo(@NonNull final BlockStreamManager manager) {
        // TODO - for every transaction type, produce the appropriate block items
    }

    @Override
    public SingleTransactionStreamBuilder transaction(@NonNull Transaction transaction) {
        return null;
    }

    @Override
    public Transaction transaction() {
        return transaction;
    }

    @Override
    public Set<AccountID> explicitRewardSituationIds() {
        return Set.of();
    }

    @Override
    public List<AccountAmount> getPaidStakingRewards() {
        return List.of();
    }

    @Override
    public boolean hasContractResult() {
        return false;
    }

    @Override
    public long getGasUsedForContractTxn() {
        return 0;
    }

    @NonNull
    @Override
    public ResponseCodeEnum status() {
        return null;
    }

    @NonNull
    @Override
    public TransactionBody transactionBody() {
        return null;
    }

    @Override
    public long transactionFee() {
        return 0;
    }

    @Override
    public SingleTransactionStreamBuilder status(@NonNull ResponseCodeEnum status) {
        return null;
    }

    @Override
    public HandleContext.TransactionCategory category() {
        return null;
    }

    @Override
    public ReversingBehavior reversingBehavior() {
        return null;
    }

    @Override
    public void nullOutSideEffectFields() {}

    @Override
    public SingleTransactionStreamBuilder syncBodyIdFromRecordId() {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder memo(@NonNull String memo) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder consensusTimestamp(@NonNull Instant now) {
        return null;
    }

    @Override
    public TransactionID transactionID() {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder transactionID(@NonNull TransactionID transactionID) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder parentConsensus(@NonNull Instant parentConsensus) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder transactionBytes(@NonNull Bytes transactionBytes) {
        return null;
    }

    @Override
    public SingleTransactionStreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
        return null;
    }
}
