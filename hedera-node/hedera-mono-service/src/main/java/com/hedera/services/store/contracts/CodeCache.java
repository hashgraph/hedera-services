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
package com.hedera.services.store.contracts;

import com.github.benmanes.caffeine.cache.Cache;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.evm.store.contracts.AbstractCodeCache;
import com.hedera.services.evm.store.contracts.utils.BytesKey;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.Code;

/**
 * Weak reference cache with expiration TTL for EVM bytecode. This cache is primarily used to store
 * bytecode pre-fetched during prepare phase (aka expand signatures) to be used later on during the
 * handle phase (aka handle transaction). The cache also has the side effect of eliminating bytecode
 * reads from the underlying store if the contract is called repeatedly during a short period of
 * time.
 *
 * <p>This cache assumes that the bytecode values are immutable, hence no logic to determine whether
 * a value is stale is present.
 */
@Singleton
public class CodeCache extends AbstractCodeCache {

    @Inject
    public CodeCache(final NodeLocalProperties properties, final EntityAccess entityAccess) {
        super(properties.prefetchCodeCacheTtlSecs(), entityAccess);
    }

    /* --- Only used by unit tests --- */
    Cache<BytesKey, Code> getCache() {
        return cache;
    }

    void cacheValue(BytesKey key, Code value) {
        cache.put(key, value);
    }
}
