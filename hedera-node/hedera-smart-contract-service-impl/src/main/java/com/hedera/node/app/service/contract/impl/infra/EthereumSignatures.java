package com.hedera.node.app.service.contract.impl.infra;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class EthereumSignatures {
    private static final int ETH_SIGS_CACHE_TTL_SECS = 15;

    private final LoadingCache<EthTxData, EthTxSigs> cache =  Caffeine.newBuilder()
                        .expireAfterAccess(ETH_SIGS_CACHE_TTL_SECS, TimeUnit.SECONDS)
                        .softValues()
                        .build(EthTxSigs::extractSignatures);

    @Inject
    public EthereumSignatures() {
        // Dagger2
    }

    public EthTxSigs impliedBy(@NonNull final EthTxData data) {
        // Since preHandle() is multi-threaded, we are happy to synchronously load the signatures
        // for this EthTxData if they are not already cached; with a 15s TTL, this will make the
        // subsequent lookup in handle() very fast
        return cache.get(data);
    }
}
