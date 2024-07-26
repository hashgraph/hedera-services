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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Fake Crypto Transfer Record Builder
 */
public class FakeCryptoTransferRecordBuilder {
    /**
     * Constructs a {@link FakeCryptoTransferRecordBuilder} instance.
     */
    public FakeCryptoTransferRecordBuilder() {}

    /**
     * Creates a {@link CryptoTransferRecordBuilder} instance.
     * @return a {@link CryptoTransferRecordBuilder} instance
     */
    public CryptoTransferRecordBuilder create() {
        return new CryptoTransferRecordBuilder() {
            @Override
            public int getNumAutoAssociations() {
                return 0;
            }

            @Override
            public Transaction transaction() {
                return Transaction.DEFAULT;
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

            @Override
            public StreamBuilder memo(@NonNull String memo) {
                return this;
            }

            @NonNull
            @Override
            public TransactionBody transactionBody() {
                return TransactionBody.DEFAULT;
            }

            @Override
            public StreamBuilder transaction(@NonNull Transaction transaction) {
                return this;
            }

            @Override
            public StreamBuilder transactionBytes(@NonNull Bytes transactionBytes) {
                return this;
            }

            @Override
            public StreamBuilder exchangeRate(@NonNull ExchangeRateSet exchangeRate) {
                return this;
            }

            @Override
            public StreamBuilder congestionMultiplier(final long congestionMultiplier) {
                return this;
            }

            @Override
            public long transactionFee() {
                return 0;
            }

            @Override
            public StreamBuilder status(@NonNull ResponseCodeEnum status) {
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
            public StreamBuilder syncBodyIdFromRecordId() {
                return null;
            }

            @Override
            public StreamBuilder consensusTimestamp(@NotNull final Instant now) {
                return null;
            }

            @Override
            public TransactionID transactionID() {
                return null;
            }

            @Override
            public StreamBuilder transactionID(@NotNull final TransactionID transactionID) {
                return null;
            }

            @Override
            public StreamBuilder parentConsensus(@NotNull final Instant parentConsensus) {
                return null;
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
