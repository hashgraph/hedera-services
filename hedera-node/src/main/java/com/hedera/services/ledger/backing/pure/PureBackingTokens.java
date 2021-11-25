package com.hedera.services.ledger.backing.pure;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.utils.EntityNum.fromTokenId;
import static java.util.stream.Collectors.toSet;

public class PureBackingTokens implements BackingStore<TokenID, MerkleToken> {
    private final Supplier<MerkleMap<EntityNum, MerkleToken>> delegate;

    public PureBackingTokens(Supplier<MerkleMap<EntityNum, MerkleToken>> delegate) {
        this.delegate = delegate;
    }

    @Override
    public MerkleToken getRef(TokenID id) {
        return delegate.get().get(fromTokenId(id));
    }

    @Override
    public MerkleToken getImmutableRef(TokenID id) {
        return delegate.get().get(fromTokenId(id));
    }

    @Override
    public void put(TokenID id, MerkleToken Token) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void remove(TokenID id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean contains(TokenID id) {
        return delegate.get().containsKey(fromTokenId(id));
    }

    @Override
    public Set<TokenID> idSet() {
        return delegate.get().keySet().stream().map(EntityNum::toGrpcTokenId).collect(toSet());
    }

    @Override
    public long size() {
        return delegate.get().size();
    }
}
