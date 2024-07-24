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

package com.hedera.node.app.service.token.impl.test.fixtures;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAirdropRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Fake Token Airdrop Record Builder
 */
public class FakeTokenAirdropRecordBuilder {

    /**
     * Constructs a {@link FakeTokenAirdropRecordBuilder} instance.
     */
    public FakeTokenAirdropRecordBuilder() {}

    /**
     * Creates a {@link TokenAirdropRecordBuilder} instance.
     * @return a {@link TokenAirdropRecordBuilder} instance
     */
    public TokenAirdropRecordBuilder create() {
        return new TokenAirdropRecordBuilder() {
            @NonNull
            @Override
            public TransactionBody transactionBody() {
                return TransactionBody.DEFAULT;
            }

            @Override
            public long transactionFee() {
                return 0;
            }

            @Override
            public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
                return this;
            }

            @Override
            public HandleContext.TransactionCategory category() {
                return HandleContext.TransactionCategory.USER;
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
            public SingleTransactionRecordBuilder memo(@NotNull String memo) {
                return null;
            }

            @Override
            public SingleTransactionRecordBuilder consensusTimestamp(@NotNull Instant now) {
                return null;
            }

            @Override
            public TransactionID transactionID() {
                return null;
            }

            @Override
            public SingleTransactionRecordBuilder transactionID(@NotNull TransactionID transactionID) {
                return null;
            }

            @Override
            public SingleTransactionRecordBuilder parentConsensus(@NotNull Instant parentConsensus) {
                return null;
            }

            @Override
            public SingleTransactionRecordBuilder transactionBytes(@NotNull Bytes transactionBytes) {
                return null;
            }

            @Override
            public SingleTransactionRecordBuilder exchangeRate(@NotNull ExchangeRateSet exchangeRate) {
                return null;
            }

            @Override
            public TokenAirdropRecordBuilder pendingAirdrops(
                    @NotNull List<PendingAirdropRecord> pendingAirdropRecords) {
                return this;
            }

            @Override
            public TokenAirdropRecordBuilder addPendingAirdrop(@NotNull PendingAirdropRecord pendingAirdropRecord) {
                return this;
            }

            @Override
            public SingleTransactionRecordBuilder transaction(@NotNull Transaction transaction) {
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
                return ResponseCodeEnum.SUCCESS;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder transferList(@NonNull final TransferList hbarTransfers) {
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder tokenTransferLists(
                    @NonNull final List<TokenTransferList> tokenTransferLists) {
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder assessedCustomFees(
                    @NonNull final List<AssessedCustomFee> assessedCustomFees) {
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder paidStakingRewards(
                    @NonNull final List<AccountAmount> paidStakingRewards) {
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder addAutomaticTokenAssociation(
                    @NonNull final TokenAssociation tokenAssociation) {
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder contractCallResult(@Nullable ContractFunctionResult result) {
                return this;
            }
        };
    }
}
