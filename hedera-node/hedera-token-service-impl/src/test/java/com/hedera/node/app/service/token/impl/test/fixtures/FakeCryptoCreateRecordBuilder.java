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
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Fake Crypto Create Record Builder
 */
public class FakeCryptoCreateRecordBuilder {
    /**
     * Constructs a {@link FakeCryptoCreateRecordBuilder} instance.
     */
    public FakeCryptoCreateRecordBuilder() {}

    /**
     * Creates a {@link CryptoCreateRecordBuilder} instance.
     * @return a {@link CryptoCreateRecordBuilder} instance
     */
    public CryptoCreateRecordBuilder create() {
        return new CryptoCreateRecordBuilder() {
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
            public StreamBuilder transaction(@NonNull Transaction transaction) {
                return this;
            }

            @Override
            public StreamBuilder transactionBytes(@NonNull Bytes transactionBytes) {
                return this;
            }

            @NonNull
            @Override
            public TransactionBody transactionBody() {
                return TransactionBody.DEFAULT;
            }

            @Override
            public long transactionFee() {
                return 0;
            }

            @NonNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder accountID(@NonNull final AccountID accountID) {
                return this;
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
            public CryptoCreateRecordBuilder evmAddress(@NonNull final Bytes evmAddress) {
                return this;
            }

            @Override
            public CryptoCreateRecordBuilder exchangeRate(@NonNull final ExchangeRateSet exchangeRate) {
                return this;
            }

            @Override
            public StreamBuilder congestionMultiplier(final long congestionMultiplier) {
                return this;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder transactionFee(@NonNull final long transactionFee) {
                return this;
            }

            @NonNull
            @Override
            public CryptoCreateRecordBuilder memo(@NonNull final String memo) {
                return this;
            }
        };
    }
}
