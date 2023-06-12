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

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Key.KeyOneOfType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Tokens.
 */
public interface ReadableTokenStore {

    /**
     * Returns the token metadata needed for signing requirements.
     *
     * @param id token id being looked up
     * @return token's metadata
     */
    @Nullable
    TokenMetadata getTokenMeta(@NonNull TokenID id);

    record TokenMetadata(
            @Nullable Key adminKey,
            @Nullable Key kycKey,
            @Nullable Key wipeKey,
            @Nullable Key freezeKey,
            @Nullable Key supplyKey,
            @Nullable Key feeScheduleKey,
            @Nullable Key pauseKey,
            @Nullable String symbol,
            boolean hasRoyaltyWithFallback,
            long treasuryNum,
            int decimals) {
        public boolean hasAdminKey() {
            return adminKey != null && !adminKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasKycKey() {
            return kycKey != null && !kycKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasWipeKey() {
            return wipeKey != null && !wipeKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasFreezeKey() {
            return freezeKey != null && !freezeKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasSupplyKey() {
            return supplyKey != null && !supplyKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasFeeScheduleKey() {
            return feeScheduleKey != null && !feeScheduleKey.key().kind().equals(KeyOneOfType.UNSET);
        }

        public boolean hasPauseKey() {
            return pauseKey != null && !pauseKey.key().kind().equals(KeyOneOfType.UNSET);
        }
    }

    /**
     * Returns all the data for a token
     *
     * @param id the token id to look up
     */
    @Nullable
    Token get(@NonNull TokenID id);
}
