package com.hedera.services.context.domain.trackers;

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

import com.hedera.services.context.properties.PropertySource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static com.hedera.services.context.domain.trackers.IssEventStatus.*;
import static org.mockito.BDDMockito.*;

class IssEventInfoTest {
	Instant firstIssTime = Instant.now().minus(30, ChronoUnit.SECONDS);
	Instant recentIssTime = Instant.now();
	int roundsToDump = 2;

	IssEventInfo subject;
	PropertySource properties;

	@BeforeEach
	private void setup() {
		properties = mock(PropertySource.class);
		given(properties.getIntProperty("iss.roundsToDump")).willReturn(roundsToDump);

		subject = new IssEventInfo(properties);
	}

	@Test
	public void startsClean() {
		// expect:
		assertEquals(NO_KNOWN_ISS, subject.status());
	}

	@Test
	public void alertWorks() {
		// when:
		subject.alert(firstIssTime);

		// then:
		assertEquals(ONGOING_ISS, subject.status());
		// and:
		assertEquals(firstIssTime, subject.consensusTimeOfRecentAlert().get());
		assertTrue(subject.shouldDumpThisRound());

		// and when:
		subject.decrementRoundsToDump();
		subject.alert(recentIssTime);
		// then:
		assertEquals(recentIssTime, subject.consensusTimeOfRecentAlert().get());
		assertTrue(subject.shouldDumpThisRound());
		subject.decrementRoundsToDump();
		assertFalse(subject.shouldDumpThisRound());
	}

	@Test
	public void relaxWorks() {
		// given:
		subject.alert(firstIssTime);

		// when:
		subject.relax();

		// then:
		assertEquals(NO_KNOWN_ISS, subject.status());
		assertEquals(0, subject.remainingRoundsToDump);
		// and:
		assertTrue(subject.consensusTimeOfRecentAlert().isEmpty());
	}
}
