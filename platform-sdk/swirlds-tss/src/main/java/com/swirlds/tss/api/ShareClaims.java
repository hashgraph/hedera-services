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

package com.swirlds.tss.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The share claims for an individual keying.
 */
public class ShareClaims {
    private final List<TssShareClaim> claims;

    /**
     * A map from share ID to index in the {@link #claims} list. Created lazily.
     */
    private Map<TssShareId, Integer> shareIdToIndexMap = null;

    /**
     * Constructor
     *
     * @param claims the list of share claims
     */
    public ShareClaims(@NonNull final List<TssShareClaim> claims) {
        this.claims = Objects.requireNonNull(claims);
    }

    /**
     * Get the list of share claims.
     *
     * @return the list of share claims
     */
    @NonNull
    public List<TssShareClaim> getClaims() {
        return claims;
    }

    /**
     * Get the index of a share ID in the {@link #claims} list.
     *
     * @param shareId the share ID to get the index of
     * @return the index of the share ID in the {@link #claims} list
     */
    public int getShareIdIndex(@NonNull final TssShareId shareId) {
        if (shareIdToIndexMap == null) {
            shareIdToIndexMap = createShareIdToIndexMap(claims);
        }

        return shareIdToIndexMap.get(shareId);
    }

    /**
     * Create a map from share ID to index in the {@link #claims} list.
     *
     * @param claims the list of share claims
     * @return the map from share ID to index
     */
    private static Map<TssShareId, Integer> createShareIdToIndexMap(@NonNull final List<TssShareClaim> claims) {
        final Map<TssShareId, Integer> shareIdToIndexMap = new HashMap<>();

        for (int i = 0; i < claims.size(); i++) {
            shareIdToIndexMap.put(claims.get(i).shareId(), i);
        }

        return shareIdToIndexMap;
    }
}
