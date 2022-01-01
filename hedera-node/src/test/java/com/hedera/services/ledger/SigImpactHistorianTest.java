package com.hedera.services.ledger;

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

import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigImpactHistorianTest {
	private static final GlobalDynamicProperties dynamicProperties = new MockGlobalDynamicProps();

	final SigImpactHistorian subject = new SigImpactHistorian(dynamicProperties);

	@Test
	void windowMgmtAsExpected() {
		assertFalse(subject.isFullWindowElapsed());
		assertNull(subject.getFirstNow());
		assertNull(subject.getNow());

		subject.advanceClockTo(firstNow);
		assertFalse(subject.isFullWindowElapsed());
		assertSame(firstNow, subject.getFirstNow());
		assertSame(firstNow, subject.getNow());

		subject.advanceClockTo(nowInFirstWindow);
		assertFalse(subject.isFullWindowElapsed());
		assertSame(firstNow, subject.getFirstNow());
		assertSame(nowInFirstWindow, subject.getNow());

		subject.advanceClockTo(nowPostFirstWindow);
		assertTrue(subject.isFullWindowElapsed());
		assertNull(subject.getFirstNow());
		assertSame(nowPostFirstWindow, subject.getNow());
	}

	private static final Instant firstNow =
			Instant.ofEpochSecond(1_234_567L, 890);
	private static final Instant nowInFirstWindow =
			firstNow.plusSeconds(dynamicProperties.changeHistorianMemorySecs());
	private static final Instant nowPostFirstWindow =
			firstNow.plusSeconds(dynamicProperties.changeHistorianMemorySecs() + 1);
}
