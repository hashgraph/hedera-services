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

import static com.hedera.services.utils.EntityNum.MISSING_NUM;

import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.utils.EntityNumPair;
import com.hedera.services.utils.MapValueListMutation;
import com.swirlds.merkle.map.MerkleMap;
import org.jetbrains.annotations.Nullable;

public class TokenRelsListMutation
        implements MapValueListMutation<EntityNumPair, MerkleTokenRelStatus> {
    private static final long MISSING_KEY = MISSING_NUM.longValue();

    final long accountNum;
    final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels;

    public TokenRelsListMutation(
            final long accountNum, final MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels) {
        this.tokenRels = tokenRels;
        this.accountNum = accountNum;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public MerkleTokenRelStatus get(final EntityNumPair key) {
        return tokenRels.get(key);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public MerkleTokenRelStatus getForModify(final EntityNumPair key) {
        return tokenRels.getForModify(key);
    }

    /** {@inheritDoc} */
    @Override
    public void put(final EntityNumPair key, final MerkleTokenRelStatus value) {
        tokenRels.put(key, value);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final EntityNumPair key) {
        tokenRels.remove(key);
    }

    /** {@inheritDoc} */
    @Override
    public void markAsHead(final MerkleTokenRelStatus node) {
        node.setPrev(MISSING_NUM.longValue());
    }

    /** {@inheritDoc} */
    @Override
    public void markAsTail(final MerkleTokenRelStatus node) {
        node.setNext(MISSING_NUM.longValue());
    }

    /** {@inheritDoc} */
    @Override
    public void updatePrev(final MerkleTokenRelStatus node, final EntityNumPair prevKey) {
        node.setPrev(prevKey.getLowOrderAsLong());
    }

    /** {@inheritDoc} */
    @Override
    public void updateNext(final MerkleTokenRelStatus node, final EntityNumPair nextKey) {
        node.setNext(nextKey.getLowOrderAsLong());
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public EntityNumPair prev(final MerkleTokenRelStatus node) {
        final var prevKey = node.prevKey();
        return prevKey == MISSING_KEY ? null : EntityNumPair.fromLongs(accountNum, prevKey);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public EntityNumPair next(final MerkleTokenRelStatus node) {
        final var nextKey = node.nextKey();
        return nextKey == MISSING_KEY ? null : EntityNumPair.fromLongs(accountNum, nextKey);
    }
}
