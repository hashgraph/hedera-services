package com.hedera.services.legacy;

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

import com.hedera.services.legacy.proto.utils.ProtoCommonUtils;
import com.hederahashgraph.api.proto.java.Timestamp;

public class TestHelper {
	private static long DEFAULT_WIND_SEC = -13; // seconds to wind back the UTC clock
	private static volatile long lastNano = 0;


	/**
	 * Gets the current UTC timestamp with default winding back seconds.
	 */
	public synchronized static Timestamp getDefaultCurrentTimestampUTC() {
		Timestamp rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
		if (rv.getNanos() == lastNano) {
			try {
				Thread.sleep(0, 1);
			} catch (InterruptedException e) {
			}
			rv = ProtoCommonUtils.getCurrentTimestampUTC(DEFAULT_WIND_SEC);
			lastNano = rv.getNanos();
		}
		return rv;
	}

}
