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
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenAssociation;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.contract.ContractFunctionResult;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
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
            @NotNull
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
            public SingleTransactionRecordBuilder status(@NotNull ResponseCodeEnum status) {
                return this;
            }

            private List<AccountAmount> paidStakingRewards;
            private List<TokenAssociation> automaticTokenAssociations;
            private ContractFunctionResult contractCallResult;

            @NotNull
            @Override
            public ResponseCodeEnum status() {
                return ResponseCodeEnum.SUCCESS;
            }

            @NotNull
            @Override
            public CryptoTransferRecordBuilder transferList(@NotNull final TransferList hbarTransfers) {
                this.transferList = hbarTransfers;
                return this;
            }

            @NotNull
            @Override
            public CryptoTransferRecordBuilder tokenTransferLists(
                    @NotNull final List<TokenTransferList> tokenTransferLists) {
                this.tokenTransferLists = tokenTransferLists;
                return this;
            }

            @NotNull
            @Override
            public CryptoTransferRecordBuilder assessedCustomFees(
                    @NotNull final List<AssessedCustomFee> assessedCustomFees) {
                this.assessedCustomFees = assessedCustomFees;
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder paidStakingRewards(
                    @NotNull final List<AccountAmount> paidStakingRewards) {
                this.paidStakingRewards = paidStakingRewards;
                return this;
            }

            @Override
            public CryptoTransferRecordBuilder addAutomaticTokenAssociation(
                    @NotNull final TokenAssociation tokenAssociation) {
                this.automaticTokenAssociations = Arrays.asList(tokenAssociation);
                return this;
            }

            @NotNull
            @Override
            public CryptoTransferRecordBuilder contractCallResult(@Nullable ContractFunctionResult result) {
                this.contractCallResult = result;
                return this;
            }
        };
    }
}
