/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.validators;

import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;

public class ApproveAllowanceValidatorTest extends CryptoTokenHandlerTestBase {
    //        @Test
    //        void failsToUpdateSpenderIfWrongOwner() {
    //            final var serials = List.of(1, 2);
    //
    //            assertThatThrownBy(() -> updateSpender(readableTokenStore, readableNftStore,
    //                    ownerId, spenderId, nonFungibleTokenId, serials))
    //                    .isInstanceOf(HandleException.class)
    //                    .has(responseCode(SENDER_DOES_NOT_OWN_NFT_SERIAL_NO));
    //        }

    //
    //    @Test
    //    void updatesSpenderAsExpected() {
    //        nft1.setOwner(ownerId);
    //        nft2.setOwner(ownerId);
    //
    //        given(tokenStore.loadUniqueToken(tokenId, serial1)).willReturn(nft1);
    //        given(tokenStore.loadUniqueToken(tokenId, serial2)).willReturn(nft2);
    //        given(tokenStore.loadToken(tokenId)).willReturn(token);
    //        given(token.getTreasury()).willReturn(treasury);
    //        given(treasury.getId()).willReturn(ownerId);
    //
    //        updateSpender(tokenStore, ownerId, spenderId, tokenId, List.of(serial1, serial2));
    //
    //        assertEquals(spenderId, nft1.getSpender());
    //        assertEquals(spenderId, nft2.getSpender());
    //    }
}
