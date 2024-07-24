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

import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * An implementation of {@link SingleTransactionRecordBuilder} that produces block items for a single user or
 * synthetic transaction; that is, the "input" block item with a {@link Transaction} and "output" block items
 * with a {@link TransactionResult} and, optionally, {@link TransactionOutput}.
 *
 */
public class IoBlockItemsBuilder implements SingleTransactionRecordBuilder {
    private final ReversingBehavior reversingBehavior;
    private final ExternalizedRecordCustomizer customizer;
    private final HandleContext.TransactionCategory category;

    public IoBlockItemsBuilder(
            @NonNull final ReversingBehavior reversingBehavior,
            @NonNull final ExternalizedRecordCustomizer customizer,
            @NonNull final HandleContext.TransactionCategory category) {
        this.reversingBehavior = reversingBehavior;
        this.customizer = customizer;
        this.category = category;
    }

    /**
     * Builds the block items for the transaction.
     * @return the block items
     */
    public List<BlockItem> build() {
        // TODO - for every transaction type, produce the appropriate block items
        return Collections.emptyList();
    }

    @Override
    public SingleTransactionRecordBuilder transaction(@NonNull Transaction transaction) {
        return null;
    }

    @Override
    public Transaction transaction() {
        return null;
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
    public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
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
    public SingleTransactionRecordBuilder syncBodyIdFromRecordId() {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder memo(@NonNull String memo) {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder consensusTimestamp(@NonNull Instant now) {
        return null;
    }

    @Override
    public TransactionID transactionID() {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder transactionID(@NonNull TransactionID transactionID) {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder parentConsensus(@NonNull Instant parentConsensus) {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder transactionBytes(@NonNull Bytes transactionBytes) {
        return null;
    }

    @Override
    public SingleTransactionRecordBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
        return null;
    }
}
