package com.hedera.services.store.contracts;

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

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.utils.EntityNum;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Weak reference cache with expiration TTL for EVM bytecode. This cache is primarily used
 * to store bytecode pre-fetched during prepare phase (aka expand signatures) to be used
 * later on during the handle phase (aka handle transaction). The cache also has the side
 * effect of eliminating bytecode reads from the underlying store if the contract is called
 * repeatedly during a short period of time.
 * <p>
 * This cache assumes that the bytecode values are immutable, hence no logic to determine
 * whether a value is stale is present.
 */
@Singleton
public class CodeCache {
    LoadingCache<BytesKey, Code> cache;
    MutableEntityAccess entityAccess;

    @Inject
    public CodeCache(NodeLocalProperties properties, MutableEntityAccess entityAccess) {
        this.entityAccess = entityAccess;

        CacheLoader<BytesKey, Code> loader = key -> {
            final var acctId = EntityNum.fromAddressBytes(key.getArray());
            var codeBytes = entityAccess.fetchCode(acctId);
            return new Code(codeBytes, Hash.hash(codeBytes));
        };

        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(properties.prefetchCodeCacheTtlSecs(), TimeUnit.SECONDS)
                .weakValues()
                .build(loader);
    }

    public Code get(Address address) {
        return cache.get(new BytesKey(address.toArray()));
    }

    public void invalidate(Address address) {
        cache.invalidate(new BytesKey(address.toArray()));
    }

    public long size() { return cache.estimatedSize(); }

    public static class BytesKey {
        byte[] array;

        public BytesKey(byte[] array) {
            this.array = array;
        }

        public byte[] getArray() { return array; }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            BytesKey bytesKey = (BytesKey) o;
            return Arrays.equals(array, bytesKey.array);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }
    }
}
