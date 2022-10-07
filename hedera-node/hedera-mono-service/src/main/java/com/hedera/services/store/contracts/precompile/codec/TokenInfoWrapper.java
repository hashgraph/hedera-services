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

import com.hederahashgraph.api.proto.java.TokenID;

public record TokenInfoWrapper(TokenID tokenID, long serialNumber) {
    private static final long INVALID_SERIAL_NUMBER = -1;

    public static TokenInfoWrapper forNonFungibleToken(
            final TokenID tokenId, final long serialNumber) {
        return new TokenInfoWrapper(tokenId, serialNumber);
    }

    public static TokenInfoWrapper forFungibleToken(final TokenID tokenId) {
        return initializeWithTokenId(tokenId);
    }

    public static TokenInfoWrapper forToken(final TokenID tokenId) {
        return initializeWithTokenId(tokenId);
    }

    private static TokenInfoWrapper initializeWithTokenId(final TokenID tokenId) {
        return new TokenInfoWrapper(tokenId, INVALID_SERIAL_NUMBER);
    }
}
