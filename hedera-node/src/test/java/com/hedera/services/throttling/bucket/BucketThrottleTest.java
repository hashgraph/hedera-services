package com.hedera.services.throttling.bucket;

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

import com.swirlds.common.throttle.Throttle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.services.throttling.bucket.BucketThrottle.EFFECTIVELY_UNLIMITED_CAPACITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

class BucketThrottleTest {
	double amount = 123.0;

	Throttle p;
	Throttle o;
	Throttle s;

	BucketThrottle subject;
	BucketThrottle overflow;
	BucketThrottle spillover;

	@BeforeEach
	private void setup() {
		p = mock(Throttle.class);
		o = mock(Throttle.class);
		s = mock(Throttle.class);

		subject = new BucketThrottle("P", p);
		overflow = new BucketThrottle("O", o);
		spillover = new BucketThrottle("S", s);
	}

	@Test
	public void autoReconfiguresWhenInsufficientCapacity() {
		// given:
		subject = new BucketThrottle("T", new Throttle(0.358, 2.6));

		// when:
		var avail = subject.hasAvailableCapacity(1.0);

		// then:
		assertTrue(avail);
		Assertions.assertEquals(2.8, subject.primary.getBurstPeriod(), 0.01);
		Assertions.assertEquals(.358, subject.primary.getTps(), 0.01);
	}

	@Test
	void usesPrimary() {
		given(p.allow(amount)).willReturn(true);

		// expect:
		assertTrue(subject.hasAvailableCapacity(amount));
	}

	@Test
	void usesOverflowIfAvailable() {
		// setup:
		subject.setOverflow(overflow);

		given(p.allow(amount)).willReturn(false);
		given(o.allow(amount)).willReturn(true);

		// expect:
		assertTrue(subject.hasAvailableCapacity(amount));
	}

	@Test
	void chainingWorks() {
		// setup:
		subject.setOverflow(overflow);
		overflow.setOverflow(spillover);

		given(p.allow(amount)).willReturn(false);
		given(o.allow(amount)).willReturn(false);
		given(s.allow(amount)).willReturn(true);

		// expect:
		assertTrue(subject.hasAvailableCapacity(amount));
	}

	@Test
	public void representsExpected() {
		givenRealThrottles();
		// and:
		overflow.setOverflow(spillover);
		subject.setOverflow(overflow);

		// when:
		var repr = subject.toString();
		// and:
		var expected = "Bucket{name=P, cap=100.0, bp=2.0, " +
				"overflow=Bucket{name=O, cap=UNLIMITED, bp=1.0, " +
				"overflow=Bucket{name=S, cap=100.0, bp=0.5}}}";

		// then:
		assertEquals(expected, repr);
	}

	private void givenRealThrottles() {
		p = new Throttle(50.0, 2.0);
		o = new Throttle(EFFECTIVELY_UNLIMITED_CAPACITY, 1.0);
		s = new Throttle(200.0, 0.5);

		subject = new BucketThrottle("P", p);
		overflow = new BucketThrottle("O", o);
		spillover = new BucketThrottle("S", s);
	}
}
