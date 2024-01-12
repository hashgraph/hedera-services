/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.store.contracts;

import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.proxyBytecodeFor;
import static java.util.Objects.requireNonNull;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.evm.store.contracts.utils.BytesKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.Code;
import org.hyperledger.besu.evm.code.CodeFactory;

public class AbstractCodeCache {
    protected final HederaEvmEntityAccess entityAccess;
    protected final Cache<BytesKey, Code> cache;

    public AbstractCodeCache(final int expirationCacheTime, final HederaEvmEntityAccess entityAccess) {
        this.entityAccess = entityAccess;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(expirationCacheTime, TimeUnit.SECONDS)
                .softValues()
                .build();
    }

    public Code getIfPresent(final Address address) {
        final var cacheKey = new BytesKey(address.toArray());

        final boolean isToken = entityAccess.isTokenAccount(address);
        if (!entityAccess.isUsable(address) && !isToken) {
            cache.invalidate(cacheKey);
            return null;
        }

        var code = cache.getIfPresent(cacheKey);

        if (code != null) {
            return code;
        }

        if (isToken) {
            final var interpolatedBytecode = proxyBytecodeFor(address);
            code = CodeFactory.createCode(interpolatedBytecode, 0, false);
            cache.put(cacheKey, code);
            return code;
        }

        final var bytecode = entityAccess.fetchCodeIfPresent(address);
        if (bytecode != null) {
            code = CodeFactory.createCode(bytecode, 0, false);
            cache.put(cacheKey, code);
        }

        return code;
    }

    public void invalidate(Address address) {
        cache.invalidate(new BytesKey(address.toArray()));
    }

    /**
     * Invalidates the cache entry for the given address if it is present with code not equal to the given bytes.
     *
     * @param address the address to maybe invalidate
     * @param bytes the correct bytes that waive invalidation
     */
    public void invalidateIfPresentAndNot(@NonNull final Address address, @NonNull final Bytes bytes) {
        requireNonNull(bytes);
        requireNonNull(address);
        final var key = new BytesKey(address.toArray());
        final var code = cache.getIfPresent(key);
        if (code != null && !code.getBytes().equals(bytes)) {
            cache.invalidate(key);
        }
    }

    public long size() {
        return cache.estimatedSize();
    }

    @VisibleForTesting
    public Cache<BytesKey, Code> getCache() {
        return cache;
    }
}
