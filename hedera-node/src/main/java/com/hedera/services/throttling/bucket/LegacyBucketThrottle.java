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
import com.swirlds.common.throttle.Throttle;

import java.util.Optional;

public class LegacyBucketThrottle {
	private static final double RESCALE_BUFFER = 0.01;

	private final String name;
	private Optional<LegacyBucketThrottle> overflow = Optional.empty();

	Throttle primary;

	static final double EFFECTIVELY_UNLIMITED_CAPACITY = 1_000_000.0;

	public LegacyBucketThrottle(Throttle primary) {
		this.name = "<N/A>";
		this.primary = primary;
	}

	LegacyBucketThrottle(String name, Throttle primary) {
		this.name = name;
		this.primary = primary;
	}

	public boolean hasAvailableCapacity(double amount) {
		if (amount > primary.getCapacity()) {
			double targetTps = amount / primary.getBurstPeriod();
			double networkSizeRatio = amount / primary.getCapacity();
			double rescaledTargetTps = targetTps / networkSizeRatio;
			primary = new Throttle(rescaledTargetTps, amount / rescaledTargetTps + RESCALE_BUFFER);
		}
		return primary.allow(amount) || overflow.map(b -> b.hasAvailableCapacity(amount)).orElse(false);
	}

	Throttle primary() {
		return primary;
	}

	public void setOverflow(LegacyBucketThrottle overflow) {
		this.overflow = Optional.of(overflow);
	}

	public boolean hasOverflow() {
		return overflow.isPresent();
	}

	public LegacyBucketThrottle overflow() {
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
