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
package com.hedera.services.txns.crypto;

import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.aggregateNftAllowances;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.updateSpender;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.protobuf.BoolValue;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.NftAllowance;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AllowanceHelpersTest {
    final TypedTokenStore tokenStore = mock(TypedTokenStore.class);
    final Token token = mock(Token.class);
    final Account treasury = mock(Account.class);
    final Id ownerId = IdUtils.asModelId("0.0.123");
    final Id spenderId = IdUtils.asModelId("0.0.124");
    final Id tokenId = IdUtils.asModelId("0.0.125");
    final long serial1 = 1L;
    final long serial2 = 2L;

    final UniqueToken nft1 = new UniqueToken(tokenId, serial1);
    final UniqueToken nft2 = new UniqueToken(tokenId, serial2);

    @Test
    void aggregatedListCorrectly() {
        List<NftAllowance> list = new ArrayList<>();
        final var Nftid =
                NftAllowance.newBuilder()
                        .setSpender(asAccount("0.0.1000"))
                        .addAllSerialNumbers(List.of(1L, 10L))
                        .setTokenId(asToken("0.0.10001"))
                        .setOwner(asAccount("0.0.5000"))
                        .setApprovedForAll(BoolValue.of(false))
                        .build();
        final var Nftid2 =
                NftAllowance.newBuilder()
                        .setSpender(asAccount("0.0.1000"))
                        .addAllSerialNumbers(List.of(1L, 100L))
                        .setTokenId(asToken("0.0.10001"))
                        .setOwner(asAccount("0.0.5000"))
                        .setApprovedForAll(BoolValue.of(false))
                        .build();
        list.add(Nftid);
        list.add(Nftid2);
        assertEquals(4, aggregateNftAllowances(list));
    }

    @Test
    void failsToUpdateSpenderIfWrongOwner() {
        nft1.setOwner(ownerId);
        nft2.setOwner(spenderId);
        final var serials = List.of(serial1, serial2);

        given(tokenStore.loadUniqueToken(tokenId, serial1)).willReturn(nft1);
        given(tokenStore.loadUniqueToken(tokenId, serial2)).willReturn(nft2);
        given(tokenStore.loadToken(tokenId)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        final var ex =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> updateSpender(tokenStore, ownerId, spenderId, tokenId, serials));

        assertEquals(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO, ex.getResponseCode());
    }

    @Test
    void updatesSpenderAsExpected() {
        nft1.setOwner(ownerId);
        nft2.setOwner(ownerId);

        given(tokenStore.loadUniqueToken(tokenId, serial1)).willReturn(nft1);
        given(tokenStore.loadUniqueToken(tokenId, serial2)).willReturn(nft2);
        given(tokenStore.loadToken(tokenId)).willReturn(token);
        given(token.getTreasury()).willReturn(treasury);
        given(treasury.getId()).willReturn(ownerId);

        updateSpender(tokenStore, ownerId, spenderId, tokenId, List.of(serial1, serial2));

        assertEquals(spenderId, nft1.getSpender());
        assertEquals(spenderId, nft2.getSpender());
    }
}
