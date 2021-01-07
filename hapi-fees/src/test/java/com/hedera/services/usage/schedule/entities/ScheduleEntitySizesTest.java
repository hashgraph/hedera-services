package com.hedera.services.usage.schedule.entities;/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.NUM_ENTITY_ID_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.NUM_FLAGS_IN_BASE_SCHEDULE_REPRESENTATION;
import static com.hedera.services.usage.schedule.entities.ScheduleEntitySizes.NUM_RICH_INSTANT_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_RICH_INSTANT_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BOOL_SIZE;
import static org.junit.Assert.assertEquals;

@RunWith(JUnitPlatform.class)
public class ScheduleEntitySizesTest {

	ScheduleEntitySizes subject = ScheduleEntitySizes.SCHEDULE_ENTITY_SIZES;

	@Test
	public void fixedSizesAsExpected() {
		// setup:
		long expected = NUM_FLAGS_IN_BASE_SCHEDULE_REPRESENTATION * BOOL_SIZE
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ NUM_RICH_INSTANT_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_RICH_INSTANT_SIZE;

		// given:
		long actual = subject.fixedBytesInScheduleRepr();

		// expect:
		assertEquals(expected, actual);
	}

	@Test
	public void sizeWithTransactionBodyAsExpected() {
		// setup:
		var transactionBody = new byte[]{0x00, 0x01, 0x02, 0x03};
		long expected = NUM_FLAGS_IN_BASE_SCHEDULE_REPRESENTATION * BOOL_SIZE
				+ NUM_ENTITY_ID_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_ENTITY_ID_SIZE
				+ NUM_RICH_INSTANT_FIELDS_IN_BASE_SCHEDULE_REPRESENTATION * BASIC_RICH_INSTANT_SIZE
				+ transactionBody.length;

		// given:
		long actual = subject.bytesInBaseReprGiven(transactionBody);

		// expect:
		assertEquals(expected, actual);
	}

}
