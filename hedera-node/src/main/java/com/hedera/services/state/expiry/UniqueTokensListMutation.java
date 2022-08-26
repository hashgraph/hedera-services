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

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListMutation;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.Nullable;

public class UniqueTokensListMutation
        implements MapValueListMutation<EntityNumPair, MerkleUniqueToken> {

    final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;

    public UniqueTokensListMutation(
            final MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens) {
        this.uniqueTokens = uniqueTokens;
    }

    @Nullable
    @Override
    public MerkleUniqueToken get(final EntityNumPair key) {
        return uniqueTokens.get(key);
    }

    @Nullable
    @Override
    public MerkleUniqueToken getForModify(final EntityNumPair key) {
        return uniqueTokens.getForModify(key);
    }

    @Override
    public void put(final EntityNumPair key, final MerkleUniqueToken value) {
        uniqueTokens.put(key, value);
    }

    @Override
    public void remove(final EntityNumPair key) {
        uniqueTokens.remove(key);
    }

    @Override
    public void markAsHead(final MerkleUniqueToken node) {
        node.setPrev(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void markAsTail(final MerkleUniqueToken node) {
        node.setNext(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void updatePrev(final MerkleUniqueToken node, final EntityNumPair prev) {
        node.setPrev(prev.asNftNumPair());
    }

    @Override
    public void updateNext(final MerkleUniqueToken node, final EntityNumPair next) {
        node.setNext(next.asNftNumPair());
    }

    @Nullable
    @Override
    public EntityNumPair next(final MerkleUniqueToken node) {
        final var nextKey = node.getNext();
        return nextKey.equals(MISSING_NFT_NUM_PAIR) ? null : nextKey.asEntityNumPair();
    }

    @Nullable
    @Override
    public EntityNumPair prev(final MerkleUniqueToken node) {
        final var prevKey = node.getPrev();
        return prevKey.equals(MISSING_NFT_NUM_PAIR) ? null : prevKey.asEntityNumPair();
    }
}
