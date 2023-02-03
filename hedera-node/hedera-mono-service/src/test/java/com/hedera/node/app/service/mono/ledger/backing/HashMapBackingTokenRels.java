/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.ledger.backing;

import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;

public class HashMapBackingTokenRels
        implements BackingStore<Pair<AccountID, TokenID>, HederaTokenRel> {
    private Map<Pair<AccountID, TokenID>, HederaTokenRel> rels = new HashMap<>();

    @Override
    public HederaTokenRel getRef(Pair<AccountID, TokenID> id) {
        return rels.get(id);
    }

    @Override
    public void put(Pair<AccountID, TokenID> id, HederaTokenRel rel) {
        rels.put(id, rel);
    }

    @Override
    public boolean contains(Pair<AccountID, TokenID> id) {
        return rels.containsKey(id);
    }

    @Override
    public void remove(Pair<AccountID, TokenID> id) {
        rels.remove(id);
    }

    @Override
    public Set<Pair<AccountID, TokenID>> idSet() {
        return rels.keySet();
    }

    @Override
    public long size() {
        return rels.size();
    }

    @Override
    public HederaTokenRel getImmutableRef(Pair<AccountID, TokenID> id) {
        return rels.get(id);
    }
}
