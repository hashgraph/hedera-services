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

package com.hedera.services.bdd.spec.utilops.mod;

import static com.hedera.services.bdd.spec.HapiPropertySource.asSolidityAddress;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import edu.umd.cs.findbugs.annotations.NonNull;

public class LongZeroAliasTransferStrategy implements TxnModificationStrategy {
    @Override
    public boolean hasTarget(@NonNull final Descriptors.FieldDescriptor fieldDescriptor, @NonNull final Object value) {
        return value instanceof CryptoTransferTransactionBody;
    }

    @NonNull
    @Override
    public TxnModification modificationForTarget(@NonNull final TargetField targetField, final int encounterIndex) {
        return new TxnModification(
                "Replacing all CryptoTransfer numeric ids with long-zero aliases",
                (builder, spec) -> usingLongZeroAliasesOfNumericIds(builder),
                ExpectedResponse.atConsensus(SUCCESS));
    }

    private TransactionBody.Builder usingLongZeroAliasesOfNumericIds(@NonNull final TransactionBody.Builder builder) {
        final var cryptoTransfer = builder.getCryptoTransfer();
        // Rebuild the CryptoTransferTransactionBody with
        // long-zero aliases in place of numeric account ids
        builder.setCryptoTransfer(cryptoTransfer.toBuilder()
                .setTransfers(usingLongZeroAliasesOfNumericIds(cryptoTransfer.getTransfers()))
                .clearTokenTransfers()
                .addAllTokenTransfers(cryptoTransfer.getTokenTransfersList().stream()
                        .map(this::usingLongZeroAliasesOfNumericIds)
                        .toList())
                .build());
        return builder;
    }

    private TokenTransferList usingLongZeroAliasesOfNumericIds(@NonNull final TokenTransferList tokenTransferList) {
        return tokenTransferList.toBuilder()
                .clearTransfers()
                .clearNftTransfers()
                .addAllTransfers(tokenTransferList.getTransfersList().stream()
                        .map(aa -> aa.toBuilder()
                                .setAccountID(toLongZeroAlias(aa.getAccountID()))
                                .build())
                        .toList())
                .addAllNftTransfers(tokenTransferList.getNftTransfersList().stream()
                        .map(this::toLongZeroAliases)
                        .toList())
                .build();
    }

    private TransferList usingLongZeroAliasesOfNumericIds(@NonNull final TransferList transferList) {
        return transferList.toBuilder()
                .clearAccountAmounts()
                .addAllAccountAmounts(transferList.getAccountAmountsList().stream()
                        .map(aa -> aa.toBuilder()
                                .setAccountID(toLongZeroAlias(aa.getAccountID()))
                                .build())
                        .toList())
                .build();
    }

    private NftTransfer toLongZeroAliases(@NonNull final NftTransfer nftTransfer) {
        return nftTransfer.toBuilder()
                .setSenderAccountID(toLongZeroAlias(nftTransfer.getSenderAccountID()))
                .setReceiverAccountID(toLongZeroAlias(nftTransfer.getReceiverAccountID()))
                .build();
    }

    /**
     * Replace numeric ids with long-zero aliases. Leave non-numeric account ids
     * unchanged.
     *
     * @param accountId the account id
     * @return the account id with numeric ids replaced with long-zero aliases
     */
    private AccountID toLongZeroAlias(@NonNull final AccountID accountId) {
        final var num = accountId.getAccountNum();
        if (num == 0) {
            return accountId;
        } else {
            return accountId.toBuilder()
                    .setAlias(ByteString.copyFrom(asSolidityAddress(accountId)))
                    .build();
        }
    }
}
