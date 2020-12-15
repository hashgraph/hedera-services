package com.hedera.services.utils;

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

import org.junit.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;

@RunWith(JUnitPlatform.class)
public class HederaDateTimeFormatterTest {
	@Test
	public void shouldFormatInstantCorrectly() {
		Instant instant = Instant.EPOCH
				.plus(18500, ChronoUnit.DAYS)
				.plus(12, ChronoUnit.HOURS)
				.plus(34, ChronoUnit.MINUTES)
				.plus(56, ChronoUnit.SECONDS)
				.plusNanos(78900);
		assertEquals("2020-08-26T12_34_56.000078900Z", HederaDateTimeFormatter.format(instant));
	}
}
