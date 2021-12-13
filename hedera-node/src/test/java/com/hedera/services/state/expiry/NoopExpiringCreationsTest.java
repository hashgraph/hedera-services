package com.hedera.services.state.expiry;

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

import static com.hedera.services.state.expiry.NoopExpiringCreations.NOOP_EXPIRING_CREATIONS;
import static org.junit.jupiter.api.Assertions.*;

class NoopExpiringCreationsTest {
	@Test
	void methodsAsExpected() {
		assertDoesNotThrow(() -> NOOP_EXPIRING_CREATIONS.setLedger(null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.saveExpiringRecord(null, null, 0L, 0L));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createTopLevelRecord(0L, null, null, null, null, null, null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createSuccessfulSyntheticRecord(null, null, null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createUnsuccessfulSyntheticRecord(null));
		assertThrows(UnsupportedOperationException.class, () ->
				NOOP_EXPIRING_CREATIONS.createInvalidFailureRecord(null, null));
	}
}
