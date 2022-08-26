/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.codec;

import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.List;

public record TokenTransferWrapper(
        List<SyntheticTxnFactory.NftExchange> nftExchanges,
        List<SyntheticTxnFactory.FungibleTokenTransfer> fungibleTransfers) {
    public TokenTransferList.Builder asGrpcBuilder() {
        final var builder = TokenTransferList.newBuilder();

        /* The same token type cannot meaningfully have both NFT exchanges and fungible exchanges,
         * so we arbitrarily give priority to the non-fungible exchange type, and let error codes be
         * assigned downstream. */
        if (!fungibleTransfers.isEmpty()) {
            final var type = fungibleTransfers.get(0).getDenomination();
            builder.setToken(type);
            for (final var transfer : fungibleTransfers) {
                if (transfer.sender() != null) {
                    builder.addTransfers(transfer.senderAdjustment());
                }
                if (transfer.receiver() != null) {
                    builder.addTransfers(transfer.receiverAdjustment());
                }
            }
        } else if (!nftExchanges.isEmpty()) {
            final var type = nftExchanges.get(0).getTokenType();
            builder.setToken(type);
            for (final var exchange : nftExchanges) {
                builder.addNftTransfers(exchange.asGrpc());
            }
        }
        return builder;
    }
}
