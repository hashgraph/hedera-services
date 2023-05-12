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

import static com.hedera.node.app.service.mono.utils.EntityNum.MISSING_NUM;

import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.migration.TokenRelStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.MapValueListMutation;
import edu.umd.cs.findbugs.annotations.Nullable;

public class TokenRelsListMutation implements MapValueListMutation<EntityNumPair, HederaTokenRel> {
    private static final long MISSING_KEY = MISSING_NUM.longValue();

    final long accountNum;
    final TokenRelStorageAdapter tokenRels;

    public TokenRelsListMutation(final long accountNum, final TokenRelStorageAdapter tokenRels) {
        this.tokenRels = tokenRels;
        this.accountNum = accountNum;
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public HederaTokenRel get(final EntityNumPair key) {
        return tokenRels.get(key);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public HederaTokenRel getForModify(final EntityNumPair key) {
        return tokenRels.getForModify(key);
    }

    /** {@inheritDoc} */
    @Override
    public void put(final EntityNumPair key, final HederaTokenRel value) {
        tokenRels.put(key, value);
    }

    /** {@inheritDoc} */
    @Override
    public void remove(final EntityNumPair key) {
        tokenRels.remove(key);
    }

    /** {@inheritDoc} */
    @Override
    public void markAsHead(final HederaTokenRel node) {
        node.setPrev(MISSING_NUM.longValue());
    }

    /** {@inheritDoc} */
    @Override
    public void markAsTail(final HederaTokenRel node) {
        node.setNext(MISSING_NUM.longValue());
    }

    /** {@inheritDoc} */
    @Override
    public void updatePrev(final HederaTokenRel node, final EntityNumPair prevKey) {
        node.setPrev(prevKey.getLowOrderAsLong());
    }

    /** {@inheritDoc} */
    @Override
    public void updateNext(final HederaTokenRel node, final EntityNumPair nextKey) {
        node.setNext(nextKey.getLowOrderAsLong());
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public EntityNumPair prev(final HederaTokenRel node) {
        final var prevKey = node.getPrev();
        return prevKey == MISSING_KEY ? null : EntityNumPair.fromLongs(accountNum, prevKey);
    }

    /** {@inheritDoc} */
    @Nullable
    @Override
    public EntityNumPair next(final HederaTokenRel node) {
        final var nextKey = node.getNext();
        return nextKey == MISSING_KEY ? null : EntityNumPair.fromLongs(accountNum, nextKey);
    }
}
