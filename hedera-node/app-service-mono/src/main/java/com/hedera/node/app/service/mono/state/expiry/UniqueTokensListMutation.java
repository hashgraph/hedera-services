/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.expiry;

import static com.hedera.node.app.service.mono.utils.NftNumPair.MISSING_NFT_NUM_PAIR;

import com.hedera.node.app.service.mono.state.migration.UniqueTokenAdapter;
import com.hedera.node.app.service.mono.state.migration.UniqueTokenMapAdapter;
import com.hedera.node.app.service.mono.store.models.NftId;
import com.hedera.node.app.service.mono.utils.MapValueListMutation;
import edu.umd.cs.findbugs.annotations.Nullable;

public class UniqueTokensListMutation implements MapValueListMutation<NftId, UniqueTokenAdapter> {

    final UniqueTokenMapAdapter uniqueTokens;

    public UniqueTokensListMutation(final UniqueTokenMapAdapter uniqueTokens) {
        this.uniqueTokens = uniqueTokens;
    }

    @Nullable
    @Override
    public UniqueTokenAdapter get(final NftId key) {
        return uniqueTokens.get(key);
    }

    @Nullable
    @Override
    public UniqueTokenAdapter getForModify(final NftId key) {
        return uniqueTokens.getForModify(key);
    }

    @Override
    public void put(final NftId key, final UniqueTokenAdapter value) {
        uniqueTokens.put(key, value);
    }

    @Override
    public void remove(final NftId key) {
        uniqueTokens.remove(key);
    }

    @Override
    public void markAsHead(final UniqueTokenAdapter node) {
        node.setPrev(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void markAsTail(final UniqueTokenAdapter node) {
        node.setNext(MISSING_NFT_NUM_PAIR);
    }

    @Override
    public void updatePrev(final UniqueTokenAdapter node, final NftId prev) {
        node.setPrev(prev.asNftNumPair());
    }

    @Override
    public void updateNext(final UniqueTokenAdapter node, final NftId next) {
        node.setNext(next.asNftNumPair());
    }

    @Nullable
    @Override
    public NftId next(final UniqueTokenAdapter node) {
        final var nextKey = node.getNext();
        return nextKey.equals(MISSING_NFT_NUM_PAIR) ? null : nextKey.nftId();
    }

    @Nullable
    @Override
    public NftId prev(final UniqueTokenAdapter node) {
        final var prevKey = node.getPrev();
        return prevKey.equals(MISSING_NFT_NUM_PAIR) ? null : prevKey.nftId();
    }
}
