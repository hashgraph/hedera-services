package com.hedera.services.txns.diligence;

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

import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.services.txns.diligence.NoopDuplicateClassifier.NOOP_DUPLICATE_CLASSIFIER;

@RunWith(JUnitPlatform.class)
class NoopDuplicateClassifierTest {
	@Test
	public void nothingMuchHappens() {
		// expect:
		assertDoesNotThrow(NOOP_DUPLICATE_CLASSIFIER::incorporateCommitment);
		assertDoesNotThrow(NOOP_DUPLICATE_CLASSIFIER::shiftDetectionWindow);
		assertEquals(BELIEVED_UNIQUE, NOOP_DUPLICATE_CLASSIFIER.duplicityOfActiveTxn());
	}
}
