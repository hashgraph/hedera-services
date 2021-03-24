package com.hedera.services.throttling.bucket;

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

import com.google.common.base.MoreObjects;

public class CapacityTest {
	private final double capacityRequired;
	private final LegacyBucketThrottle bucket;

	public CapacityTest(double capacityRequired, LegacyBucketThrottle bucket) {
		this.capacityRequired = capacityRequired;
		this.bucket = bucket;
		/* Ensure the bucket re-configures its primary throttle if necessary. */
		bucket.hasAvailableCapacity(capacityRequired);
	}

	public boolean isAvailable() {
		return bucket.hasAvailableCapacity(capacityRequired);
	}

	public double getCapacityRequired() {
		return capacityRequired;
	}

	public LegacyBucketThrottle getBucket() {
		return bucket;
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper("Test");
		helper.add("req", String.format("%.2f", capacityRequired));
		helper.add("in", bucket.toString());
		return helper.toString();
	}
}
