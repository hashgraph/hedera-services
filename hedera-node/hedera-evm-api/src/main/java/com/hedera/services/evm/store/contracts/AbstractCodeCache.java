package com.hedera.services.evm.store.contracts;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hedera.services.evm.store.contracts.utils.BytesKey;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.evm.Code;

import java.util.concurrent.TimeUnit;

import static com.hedera.services.evm.store.contracts.utils.TokenAccountUtils.bytecodeForToken;

public class AbstractCodeCache {
	private final HederaEvmEntityAccess entityAccess;
	private final Cache<BytesKey, Code> cache;

	public AbstractCodeCache(final int cacheTTL, final HederaEvmEntityAccess entityAccess) {
		this.entityAccess = entityAccess;
		this.cache =
				Caffeine.newBuilder()
						.expireAfterAccess(cacheTTL, TimeUnit.SECONDS)
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
