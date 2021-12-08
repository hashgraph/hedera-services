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

import java.time.Instant;
import java.util.Collections;

import static com.hedera.services.records.NoopRecordsHistorian.NOOP_RECORDS_HISTORIAN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class NoopRecordsHistorianTest {
	@Test
	void nothingMuchHappens() {
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::clearHistory);
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::noteNewExpirationEvents);
		assertDoesNotThrow(NOOP_RECORDS_HISTORIAN::saveExpirableTransactionRecords);
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.setCreator(null));
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.revertChildRecordsFromSource(0));
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.trackFollowingChildRecord(0, null, null));
		assertDoesNotThrow(() -> NOOP_RECORDS_HISTORIAN.trackPrecedingChildRecord(0, null, null));
		assertNull(NOOP_RECORDS_HISTORIAN.lastCreatedTopLevelRecord());
		assertFalse(NOOP_RECORDS_HISTORIAN.hasPrecedingChildRecords());
		assertFalse(NOOP_RECORDS_HISTORIAN.hasFollowingChildRecords());
		assertSame(Instant.EPOCH, NOOP_RECORDS_HISTORIAN.nextFollowingChildConsensusTime());
		assertSame(Collections.emptyList(), NOOP_RECORDS_HISTORIAN.getPrecedingChildRecords());
		assertSame(Collections.emptyList(), NOOP_RECORDS_HISTORIAN.getFollowingChildRecords());
		assertEquals(0, NOOP_RECORDS_HISTORIAN.nextChildRecordSourceId());
	}
}
