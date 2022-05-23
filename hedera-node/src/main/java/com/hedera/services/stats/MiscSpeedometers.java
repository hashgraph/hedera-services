package com.hedera.services.stats;

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

import com.swirlds.common.system.Platform;
import com.swirlds.common.statistics.StatsSpeedometer;

public class MiscSpeedometers {
	private final SpeedometerFactory speedometer;

	StatsSpeedometer syncVerifications;
	StatsSpeedometer platformTxnRejections;

	public MiscSpeedometers(final SpeedometerFactory speedometer, final double halfLife) {
		this.speedometer = speedometer;

		syncVerifications = new StatsSpeedometer(halfLife);
		platformTxnRejections = new StatsSpeedometer(halfLife);
	}

	public void registerWith(final Platform platform) {
		platform.addAppStatEntry(
				speedometer.from(
						Names.SYNC_VERIFICATIONS,
						Descriptions.SYNC_VERIFICATIONS,
						syncVerifications));
		platform.addAppStatEntry(
				speedometer.from(
						Names.PLATFORM_TXN_REJECTIONS,
						Descriptions.PLATFORM_TXN_REJECTIONS,
						platformTxnRejections));
	}

	public void cycleSyncVerifications() {
		syncVerifications.update(1);
	}

	public void cyclePlatformTxnRejections() {
		platformTxnRejections.update(1);
	}

	public static final class Names {
		static final String SYNC_VERIFICATIONS = "sigVerifySync/sec";
		static final String PLATFORM_TXN_REJECTIONS = "platformTxnNotCreated/sec";

		private Names() {
			throw new UnsupportedOperationException("Utility Class");
		}
	}

	public static final class Descriptions {
		static final String SYNC_VERIFICATIONS =
				"number of transactions received per second that must be verified synchronously in handleTransaction";
		static final String PLATFORM_TXN_REJECTIONS =
				"number of platform transactions not created per second";

		private Descriptions() {
			throw new UnsupportedOperationException("Utility Class");
		}
	}
}
