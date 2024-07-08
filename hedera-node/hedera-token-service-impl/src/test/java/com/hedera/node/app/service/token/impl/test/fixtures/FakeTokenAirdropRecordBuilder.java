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
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.PendingAirdropRecord;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.records.TokenAirdropRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
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

            private TransferList transferList;
            private List<TokenTransferList> tokenTransferLists;
            private List<AssessedCustomFee> assessedCustomFees;

            @Override
            public SingleTransactionRecordBuilder status(@NonNull ResponseCodeEnum status) {
                return this;
            }

            private List<AccountAmount> paidStakingRewards;
            private List<TokenAssociation> automaticTokenAssociations;
            private ContractFunctionResult contractCallResult;
            private List<PendingAirdropRecord> pendingAirdropRecords;

            @Override
            public TokenAirdropRecordBuilder pendingAirdrops(
                    @NotNull List<PendingAirdropRecord> pendingAirdropRecords) {
                this.pendingAirdropRecords = pendingAirdropRecords;
                return this;
            }

            @Override
            public TokenAirdropRecordBuilder addPendingAirdrop(@NotNull PendingAirdropRecord pendingAirdropRecord) {
                this.pendingAirdropRecords = List.of(pendingAirdropRecord);
                return this;
            }

            @NonNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder transferList(@NonNull final TransferList hbarTransfers) {
                this.transferList = hbarTransfers;
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder tokenTransferLists(
                    @NonNull final List<TokenTransferList> tokenTransferLists) {
                this.tokenTransferLists = tokenTransferLists;
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder assessedCustomFees(
                    @NonNull final List<AssessedCustomFee> assessedCustomFees) {
                this.assessedCustomFees = assessedCustomFees;
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder paidStakingRewards(
                    @NonNull final List<AccountAmount> paidStakingRewards) {
                this.paidStakingRewards = paidStakingRewards;
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder addAutomaticTokenAssociation(
                    @NonNull final TokenAssociation tokenAssociation) {
                this.automaticTokenAssociations = Arrays.asList(tokenAssociation);
                return this;
            }

            @NonNull
            @Override
            public CryptoTransferRecordBuilder contractCallResult(@Nullable ContractFunctionResult result) {
                this.contractCallResult = result;
                return this;
            }
        };
    }
}
