package com.hedera.services.legacy.unit.utils;

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

import com.hedera.services.state.logic.NetworkCtxManager;
import org.junit.Assert;
import org.junit.Test;
import java.time.Instant;

public class CommonUtilsTest {
	@Test
	public void isSameDayUTCTest() {
		Instant instant1_1 = Instant.parse("2019-08-14T23:59:59.0Z");
		Instant instant1_2 = Instant.parse("2019-08-14T23:59:59.99999Z");
		Instant instant2_1 = Instant.parse("2019-08-14T24:00:00.0Z");
		Instant instant2_2 = Instant.parse("2019-08-15T00:00:00.0Z");
		Instant instant2_3 = Instant.parse("2019-08-15T00:00:00.00001Z");

		Assert.assertTrue(NetworkCtxManager.inSameUtcDay(instant1_1, instant1_2));

		Assert.assertFalse(NetworkCtxManager.inSameUtcDay(instant1_1, instant2_1));
		Assert.assertFalse(NetworkCtxManager.inSameUtcDay(instant1_2, instant2_1));

		Assert.assertTrue(NetworkCtxManager.inSameUtcDay(instant2_1, instant2_2));
		Assert.assertTrue(NetworkCtxManager.inSameUtcDay(instant2_2, instant2_3));
	}
}
