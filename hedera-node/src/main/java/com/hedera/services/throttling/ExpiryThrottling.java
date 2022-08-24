package com.hedera.services.throttling;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.files.HybridResouceLoader;
import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExpiryThrottling {
    private final HybridResouceLoader resouceLoader;

    @Inject
    public ExpiryThrottling(final HybridResouceLoader resouceLoader) {
        this.resouceLoader = resouceLoader;
    }

    public void rebuildFromResource(final String resourceLoc) {
        throw new AssertionError("Not implemented");
    }

    @VisibleForTesting
    ThrottleBucket<MapAccessType>
}
