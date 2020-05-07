package com.hedera.services.throttling.bucket;

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

import com.swirlds.throttle.Throttle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

@RunWith(JUnitPlatform.class)
class CapacityTestTest {
	double req = 123.0;
	BucketThrottle bucket;

	CapacityTest subject;

	@BeforeEach
	private void setup() {
		bucket = mock(BucketThrottle.class);

		subject = new CapacityTest(req, bucket);
	}

	@Test
	public void delegatesAsExpected() {
		given(bucket.hasAvailableCapacity(req)).willReturn(true);

		// when:
		boolean flag = subject.isAvailable();

		// then:
		assertTrue(flag);
		// and:
		verify(bucket).hasAvailableCapacity(req);
	}

	@Test
	void toStringWorks() {
		// setup:
		var t = new Throttle(5.0, 1.0);

		// given:
		subject = new CapacityTest(1.011, new BucketThrottle("B", t));

		// when:
		var repr = subject.toString();
		// and:
		var expected = "Test{req=1.01, in=Bucket{name=B, cap=5.0, bp=1.0}}";

		// then:
		assertEquals(expected, repr);
	}
}
