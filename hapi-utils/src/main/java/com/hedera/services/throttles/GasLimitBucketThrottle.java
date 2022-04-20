package com.hedera.services.throttles;

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

import static com.hedera.services.legacy.proto.utils.CommonUtils.productWouldOverflow;

/**
 * Responsible for throttling transaction by gas limit.
 * Uses a {@link DiscreteLeakyBucket} under the hood.
 * Calculates the amount of gas that should be leaked from the bucket based on the amount of elapsed nanoseconds
 * since the last time {@link GasLimitBucketThrottle#allow(long, long)} was called.
 */
public class GasLimitBucketThrottle {

    private final DiscreteLeakyBucket bucket;
	private long lastAllowedUnits = 0L;
    private static final long ONE_SECOND_IN_NANOSECONDS = 1_000_000_000;

    /**
     * Creates an instance of the throttle with the specified capacity
     * @param capacity - the capacity for the throttle
     */
    public GasLimitBucketThrottle(long capacity) {
        this.bucket = new DiscreteLeakyBucket(capacity);
    }

    /**
     * Calculates and leaks the amount of gas that should be leaked from the bucket based on the amount of nanoseconds
     * passed as input argument. Verifies whether there is enough capacity to handle a transaction with the specified
     * gas limit. Reserves the capacity needed for the transaction if there is enough free space.
     * @param txGasLimit - the gas limit of the transaction
     * @param elapsedNanos - the amount of time passed since the last call
     * @return true if there is enough capacity, false if the transaction should be throttled
     */
    public boolean allow(long txGasLimit, long elapsedNanos) {
        if (elapsedNanos >= ONE_SECOND_IN_NANOSECONDS) {
            bucket.leak(bucket.totalCapacity());
        } else if (elapsedNanos > 0) {
            boolean wouldOverflow = productWouldOverflow(elapsedNanos, bucket.totalCapacity());
            long toLeak = wouldOverflow ? Long.MAX_VALUE / ONE_SECOND_IN_NANOSECONDS :
                    elapsedNanos * bucket.totalCapacity() / ONE_SECOND_IN_NANOSECONDS;
            bucket.leak(toLeak);
        }

        if (bucket.capacityFree() >= txGasLimit) {
            bucket.useCapacity(txGasLimit);
            lastAllowedUnits += txGasLimit;
            return true;
        } else {
            return false;
        }
    }

	void resetLastAllowedUse() {
		lastAllowedUnits = 0;
	}

	void reclaimLastAllowedUse() {
		bucket.leak(lastAllowedUnits);
		lastAllowedUnits = 0;
	}

    /**
     * Returns an instance of the {@link DiscreteLeakyBucket} used under the hood.
     * @return an instance of the {@link DiscreteLeakyBucket} used under the hood.
     */
    public DiscreteLeakyBucket bucket() {
        return bucket;
    }
}
