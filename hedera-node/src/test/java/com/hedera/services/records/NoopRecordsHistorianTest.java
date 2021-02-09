package com.hedera.services.records;

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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.services.records.NoopRecordsHistorian.NOOP_RECORDS_HISTORIAN;

class NoopRecordsHistorianTest {
	@Test
	public void nothingMuchHappens() {
		// expect:
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::addNewRecords);
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::purgeExpiredRecords);
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::addNewEntities);
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.setLedger(null));
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.setCreator(null));
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.reviewExistingRecords());
		assertTrue(NOOP_RECORDS_HISTORIAN.lastCreatedRecord().isEmpty());
	}
}
