/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.backing;

import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class HashMapBackingTokens implements BackingStore<TokenID, MerkleToken> {
    private Map<TokenID, MerkleToken> tokens = new HashMap<>();

    @Override
    public MerkleToken getRef(TokenID id) {
        return tokens.get(id);
    }

    @Override
    public void put(TokenID id, MerkleToken Token) {
        tokens.put(id, Token);
    }

    @Override
    public boolean contains(TokenID id) {
        return tokens.containsKey(id);
    }

    @Override
    public void remove(TokenID id) {
        tokens.remove(id);
    }

    @Override
    public Set<TokenID> idSet() {
        return tokens.keySet();
    }

    @Override
    public long size() {
        return tokens.size();
    }

    @Override
    public MerkleToken getImmutableRef(TokenID id) {
        return tokens.get(id);
    }
}
