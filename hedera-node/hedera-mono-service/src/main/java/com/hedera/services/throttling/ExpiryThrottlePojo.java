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
package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleBucket;

/**
 * Wrapper class so that Jackson will be able to infer the type of enums used in the expiry throttle
 * bucket.
 */
public class ExpiryThrottlePojo {
    private ThrottleBucket<MapAccessType> bucket;

    public ThrottleBucket<MapAccessType> getBucket() {
        return bucket;
    }

    public void setBucket(ThrottleBucket<MapAccessType> bucket) {
        this.bucket = bucket;
    }
}
