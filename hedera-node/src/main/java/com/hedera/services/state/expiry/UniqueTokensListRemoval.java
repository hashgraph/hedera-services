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
package com.hedera.services.state.expiry;

import static com.hedera.services.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.utils.MapValueListMutation;
import com.swirlds.virtualmap.VirtualMap;
import org.jetbrains.annotations.Nullable;

public class UniqueTokensListRemoval
        implements MapValueListMutation<UniqueTokenKey, UniqueTokenValue> {

    final VirtualMap<UniqueTokenKey, UniqueTokenValue> uniqueTokens;

    public UniqueTokensListRemoval(
            final VirtualMap<UniqueTokenKey, UniqueTokenValue> uniqueTokens) {
        this.uniqueTokens = uniqueTokens;
    }

    @Nullable
    @Override
    public UniqueTokenValue get(final UniqueTokenKey key) {
        return uniqueTokens.get(key);
    }

    @Nullable
    @Override
    public UniqueTokenValue getForModify(final UniqueTokenKey key) {
        return uniqueTokens.getForModify(key);
    }

    @Override
    public void put(final UniqueTokenKey key, final UniqueTokenValue value) {
        uniqueTokens.put(key, value);
    }

    @Override
    public void remove(final UniqueTokenKey key) {
        uniqueTokens.remove(key);
    }

    @Override
    public void markAsHead(final UniqueTokenValue node) {
        node.setPrev(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void markAsTail(final UniqueTokenValue node) {
        node.setNext(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void updatePrev(final UniqueTokenValue node, final UniqueTokenKey prev) {
        node.setPrev(prev.toNftNumPair());
    }

    @Override
    public void updateNext(final UniqueTokenValue node, final UniqueTokenKey next) {
        node.setNext(next.toNftNumPair());
    }

    @Nullable
    @Override
    public UniqueTokenKey next(final UniqueTokenValue node) {
        final var nextKey = node.getNext();
        return nextKey.equals(MISSING_NFT_NUM_PAIR) ? null : UniqueTokenKey.from(nextKey);
    }

    @Nullable
    @Override
    public UniqueTokenKey prev(final UniqueTokenValue node) {
        final var prevKey = node.getPrev();
        return prevKey.equals(MISSING_NFT_NUM_PAIR) ? null : UniqueTokenKey.from(prevKey);
    }
}
