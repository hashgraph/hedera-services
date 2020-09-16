package com.hedera.services.bdd.suites.perf;

/*-
 * ‌
 * Hedera Services Test Clients
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
import com.hedera.services.bdd.spec.HapiPropertySource;

public class PerfTestLoadSettings {
	public static final int DEFAULT_TPS = 500;
	public static final int DEFAULT_TOLERANCE_PERCENTAGE = 5;
	public static final int DEFAULT_MINS = 5;
	public static final int DEFAULT_ALLOWED_SECS_BELOW = 60;
	public static final int DEFAULT_BURST_SIZE = 5;
	public static final int DEFAULT_THREADS = 50;

	private int tps = DEFAULT_TPS;
	private int tolerancePercentage = DEFAULT_TOLERANCE_PERCENTAGE;
	private int mins = DEFAULT_MINS;
	private int allowedSecsBelow = DEFAULT_ALLOWED_SECS_BELOW;
	private int burstSize = DEFAULT_BURST_SIZE;
	private int threads = DEFAULT_THREADS;

	private HapiPropertySource ciProps = null;

	public int getTps() {
		return tps;
	}

	public int getTolerancePercentage() {
		return tolerancePercentage;
	}

	public int getMins() {
		return mins;
	}

	public int getAllowedSecsBelow() {
		return allowedSecsBelow;
	}

	public int getBurstSize() {
		return burstSize;
	}

	public int getThreads() {
		return threads;
	}

	public int getIntProperty(String property, int defaultValue) {
		if (null != ciProps && ciProps.has(property)) {
			return ciProps.getInteger(property);
		}
		return defaultValue;
	}

	public boolean getBooleanProperty(String property, boolean defaultValue) {
		if (null != ciProps && ciProps.has(property)) {
			return ciProps.getBoolean(property);
		}
		return defaultValue;
	}

	public void setFrom(HapiPropertySource ciProps) {
		this.ciProps = ciProps;
		if (ciProps.has("tps")) {
			tps = ciProps.getInteger("tps");
		}
		if (ciProps.has("mins")) {
			mins = ciProps.getInteger("mins");
		}
		if (ciProps.has("tolerance")) {
			tolerancePercentage = ciProps.getInteger("tolerancePercentage");
		}
		if (ciProps.has("burstSize")) {
			burstSize = ciProps.getInteger("burstSize");
		}
		if (ciProps.has("allowedSecsBelow")) {
			allowedSecsBelow = ciProps.getInteger("allowedSecsBelow");
		}
		if (ciProps.has("threads")) {
			threads = ciProps.getInteger("threads");
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("tps", tps)
				.add("mins", mins)
				.add("tolerance", tolerancePercentage)
				.add("burstSize", burstSize)
				.add("allowedSecsBelow", allowedSecsBelow)
				.add("threads", threads)
				.toString();
	}
}
