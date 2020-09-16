package com.hedera.services.throttling.bucket;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import com.swirlds.throttle.Throttle;

import java.util.Optional;

public class BucketThrottle {
	private final String name;
	private final Throttle primary;
	private Optional<BucketThrottle> overflow = Optional.empty();

	static final double EFFECTIVELY_UNLIMITED_CAPACITY = 1_000_000.0;

	public BucketThrottle(Throttle primary) {
		this.name = "<N/A>";
		this.primary = primary;
	}

	BucketThrottle(String name, Throttle primary) {
		this.name = name;
		this.primary = primary;
	}

	public boolean hasAvailableCapacity(double amount) {
		return primary.allow(amount) || overflow.map(b -> b.hasAvailableCapacity(amount)).orElse(false);
	}

	Throttle primary() {
		return primary;
	}

	public void setOverflow(BucketThrottle overflow) {
		this.overflow = Optional.of(overflow);
	}

	public boolean hasOverflow() {
		return overflow.isPresent();
	}

	public BucketThrottle overflow() {
		return overflow.get();
	}

	public String name() {
		return name;
	}

	@Override
	public String toString() {
		var helper = MoreObjects.toStringHelper("Bucket");
		helper.add("name", name);
		var capacity = primary.getTps() * primary.getBurstPeriod();
		var repr = (capacity >= EFFECTIVELY_UNLIMITED_CAPACITY) ? "UNLIMITED" : String.format("%.1f", capacity);
		helper.add("cap", repr);
		helper.add("bp", String.format("%.1f", primary.getBurstPeriod()));
		overflow.ifPresent(o -> helper.add("overflow", o.toString()));
		return helper.toString();
	}
}
