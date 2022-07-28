/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenTransferWrapperTest {
    @Test
    void translatesNftExchangesAsExpected() {
        final var inputExchanges = wellKnownExchanges();
        final var nftSubject = new TokenTransferWrapper(inputExchanges, Collections.emptyList());

        final var builder = nftSubject.asGrpcBuilder();
        assertEquals(token, builder.getToken());
        final var exchanges = builder.getNftTransfersList();
        assertEquals(
                inputExchanges.stream().map(SyntheticTxnFactory.NftExchange::asGrpc).toList(),
                exchanges);
    }

    @Test
    void translatesFungibleTransfersAsExpected() {
        final var inputTransfers = wellKnownTransfers();
        final var expectedAdjustments =
                TokenTransferList.newBuilder()
                        .setToken(token)
                        .addTransfers(aaWith(anAccount, aChange))
                        .addTransfers(aaWith(otherAccount, bChange))
                        .addTransfers(aaWith(anotherAccount, cChange))
                        .build();
        final var fungibleSubject =
                new TokenTransferWrapper(Collections.emptyList(), inputTransfers);

        final var builder = fungibleSubject.asGrpcBuilder();
        assertEquals(expectedAdjustments, builder.build());
    }

    @Test
    void translatesNoopAsExpected() {
        final var nothingSubject =
                new TokenTransferWrapper(Collections.emptyList(), Collections.emptyList());

        final var builder = nothingSubject.asGrpcBuilder();
        assertEquals(TokenTransferList.getDefaultInstance(), builder.build());
    }

    private AccountAmount aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount).build();
    }

    private List<SyntheticTxnFactory.NftExchange> wellKnownExchanges() {
        return List.of(
                new SyntheticTxnFactory.NftExchange(aSerialNo, token, anAccount, otherAccount),
                new SyntheticTxnFactory.NftExchange(bSerialNo, token, anAccount, anotherAccount));
    }

    private List<SyntheticTxnFactory.FungibleTokenTransfer> wellKnownTransfers() {
        return List.of(
                new SyntheticTxnFactory.FungibleTokenTransfer(
                        Math.abs(aChange), false, token, anAccount, null),
                new SyntheticTxnFactory.FungibleTokenTransfer(
                        Math.abs(bChange), false, token, null, otherAccount),
                new SyntheticTxnFactory.FungibleTokenTransfer(
                        Math.abs(cChange), false, token, null, anotherAccount));
    }

    private final long aSerialNo = 1_234L;
    private final long bSerialNo = 2_234L;
    private final long aChange = -100L;
    private final long bChange = +75;
    private final long cChange = +25;
    private final AccountID anAccount = new Id(0, 0, 1234).asGrpcAccount();
    private final AccountID otherAccount = new Id(0, 0, 2345).asGrpcAccount();
    private final AccountID anotherAccount = new Id(0, 0, 3456).asGrpcAccount();
    private final TokenID token = new Id(0, 0, 75231).asGrpcToken();
    private final Id aNft = new Id(0, 0, 9999);
    private final Id bNft = new Id(0, 0, 10000);
}
