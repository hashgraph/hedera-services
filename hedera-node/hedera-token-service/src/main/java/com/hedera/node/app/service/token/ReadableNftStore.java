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

package com.hedera.node.app.service.token;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.common.UniqueTokenId;
import com.hedera.hapi.node.state.token.Nft;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Nfts.
 */
public interface ReadableNftStore {
    /**
     * Gets {@link Nft} data for a given {@link TokenID} and serial number.
     * @param id the token id to look up
     * @param serialNumber the serial number to look up
     * @return the {@link Nft} data for the given token id and serial number,
     * or {@code null} if the token serial doesn't exist
     */
    @Nullable
    default Nft get(@NonNull final TokenID id, final long serialNumber) {
        final var uniqueTokenId = UniqueTokenId.newBuilder()
                .tokenTypeNumber(id.tokenNum())
                .serialNumber(serialNumber)
                .build();
        return get(uniqueTokenId);
    }

    /**
     * Gets {@link Nft} data for a given {@link UniqueTokenId}.
     * @param id the unique token id to look up
     * @return the {@link Nft} data for the given unique token id, {@code null} if the id doesn't exist
     */
    @Nullable
    Nft get(@NonNull UniqueTokenId id);
}
