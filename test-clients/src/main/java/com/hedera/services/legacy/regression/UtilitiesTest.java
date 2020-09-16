package com.hedera.services.legacy.regression;

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

import org.junit.Test;

public class UtilitiesTest {

	@Test
	public void getUTCHourMinFromMillis_Test() {
		long currentTimeMillis = System.currentTimeMillis();
		int[] startHourMin = Utilities.getUTCHourMinFromMillis(currentTimeMillis);
		int[] endHourMin = Utilities.getUTCHourMinFromMillis(currentTimeMillis + 60000);
		assert endHourMin[1] == (startHourMin[1] + 1) % 60;
		if (endHourMin[1] == startHourMin[1] + 1) {
			assert endHourMin[0] == startHourMin[0];
		} else {
			assert endHourMin[0] == (startHourMin[0] + 1) % 24;
		}
	}
}
