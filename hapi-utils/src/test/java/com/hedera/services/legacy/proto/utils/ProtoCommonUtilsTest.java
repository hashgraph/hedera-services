package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API Utilities
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

import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;

import static com.hedera.services.legacy.proto.utils.ProtoCommonUtils.addSecondsToTimestamp;
import static com.hedera.services.legacy.proto.utils.ProtoCommonUtils.getCurrentInstantUTC;
import static com.hedera.services.legacy.proto.utils.ProtoCommonUtils.getCurrentTimestampUTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtoCommonUtilsTest {
	@Test
	void throwsInConstructor() {
		assertThrows(IllegalStateException.class, ProtoCommonUtils::new);
	}

	@Test
	void shouldWindTheClock() {
		final var now = getCurrentInstantUTC();

		final var after100s = getCurrentTimestampUTC(100L).getSeconds();

		assertTrue(after100s >= now.getEpochSecond() + 100L);
		assertTrue(after100s <= now.getEpochSecond() + 101L);
	}

	@Test
	void shouldAddSecondsToTimestamp() {
		final var ts123 = Timestamp.newBuilder().setSeconds(123L).setNanos(456).build();

		final var ts130 = addSecondsToTimestamp(ts123, 7L);

		assertEquals(130L, ts130.getSeconds());
		assertEquals(456, ts130.getNanos());
	}
}
