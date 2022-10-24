package com.hedera.services.evm.store.contracts;

import com.github.benmanes.caffeine.cache.Cache;
import com.hedera.services.evm.store.contracts.utils.BytesKey;
import org.hyperledger.besu.evm.Code;

public class MockAbstractCodeCache extends AbstractCodeCache {
    public MockAbstractCodeCache(int expirationCacheTime, HederaEvmEntityAccess entityAccess) {
        super(expirationCacheTime, entityAccess);
    }

    /* --- Only used by unit tests --- */
    Cache<BytesKey, Code> getCache() {
        return cache;
    }

    void cacheValue(BytesKey key, Code value) {
        cache.put(key, value);
    }
}
