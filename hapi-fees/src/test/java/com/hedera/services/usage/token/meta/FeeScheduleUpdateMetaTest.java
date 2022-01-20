package com.hedera.services.usage.token.meta;

/*-
 * ‌
 * Hedera Services API Fees
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeeScheduleUpdateMetaTest {
	@Test
	void assertEqualsWork() {
		// when:
		final var subject = new FeeScheduleUpdateMeta(1_234_567L, 22);
		final var subject2 = new FeeScheduleUpdateMeta(1_234_567L, 22);

		//then:
		assertEquals(subject, subject2);
		assertEquals(subject.hashCode(), subject2.hashCode());
	}

	@Test
	void assertGetters() {
		// when:
		final var subject = new FeeScheduleUpdateMeta(1_234_567L, 22);

		//then:
		assertEquals(1_234_567L, subject.effConsensusTime());
		assertEquals(22, subject.numBytesInNewFeeScheduleRepr());
	}

	@Test
	void toStringWorks() {
		// given:
		final var desired = "FeeScheduleUpdateMeta[effConsensusTime=1234567, numBytesInNewFeeScheduleRepr=22]";

		// when:
		final var subject = new FeeScheduleUpdateMeta(1_234_567L, 22);

		// then:
		assertEquals(desired, subject.toString());
	}
}
