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
package com.hedera.services.evm.store.contracts;

import static com.hedera.services.evm.store.contracts.HederaEvmWorldStateTokenAccount.bytecodeForToken;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.services.evm.store.contracts.utils.BytesKey;
import java.util.concurrent.TimeUnit;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

public class AbstractCodeCache {
    protected final HederaEvmEntityAccess entityAccess;
    protected final Cache<BytesKey, Code> cache;

    public AbstractCodeCache(
            final int expirationCacheTime, final HederaEvmEntityAccess entityAccess) {
        this.entityAccess = entityAccess;
        this.cache =
                Caffeine.newBuilder()
                        .expireAfterAccess(expirationCacheTime, TimeUnit.SECONDS)
                        .softValues()
                        .build();
    }

    public Code getIfPresent(final Address address) {
        final var cacheKey = new BytesKey(address.toArray());

        var code = cache.getIfPresent(cacheKey);

        if (code != null) {
            return code;
        }

        if (entityAccess.isTokenAccount(address)) {
            final var interpolatedBytecode = bytecodeForToken(address);
            code = Code.createLegacyCode(interpolatedBytecode, Hash.hash(interpolatedBytecode));
            cache.put(cacheKey, code);
            return code;
        }

        final var bytecode = entityAccess.fetchCodeIfPresent(address);
        if (bytecode != null) {
            code = Code.createLegacyCode(bytecode, Hash.hash(bytecode));
            cache.put(cacheKey, code);
        }

        return code;
    }

    public void invalidate(Address address) {
        cache.invalidate(new BytesKey(address.toArray()));
    }

    public long size() {
        return cache.estimatedSize();
    }
}
