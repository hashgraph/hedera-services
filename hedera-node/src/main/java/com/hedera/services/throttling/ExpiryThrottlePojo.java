package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;

public class ExpiryThrottlePojo {
    private ThrottleBucket<MapAccessType> bucket;

    public ThrottleBucket<MapAccessType> getBucket() {
        return bucket;
    }

    public void setBucket(ThrottleBucket<MapAccessType> bucket) {
        this.bucket = bucket;
    }
}
