package com.hedera.services.txns.crypto;

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

import static org.junit.jupiter.api.Assertions.*;

class UnusableAutoCreationTest {
	@Test
	void methodsAsExpected() {
		final var subject = UnusableAutoCreation.UNUSABLE_AUTO_CREATION;

		assertDoesNotThrow(subject::reset);
		assertDoesNotThrow(() -> subject.setFeeCalculator(null));
		assertFalse(subject.reclaimPendingAliases());
		assertThrows(UnsupportedOperationException.class, () -> subject.submitRecordsTo(null));
		assertThrows(UnsupportedOperationException.class, () -> subject.createFromTrigger(null));
	}
}
