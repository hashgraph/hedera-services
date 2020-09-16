package com.hedera.services.txns.validation;

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

import com.hederahashgraph.api.proto.java.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static org.junit.jupiter.api.Assertions.*;

@RunWith(JUnitPlatform.class)
class PureValidationTest {
	Instant now = Instant.now();
	long impossiblySmallSecs = Instant.MIN.getEpochSecond() - 1;
	int impossiblySmallNanos = -1;
	long impossiblyBigSecs = Instant.MAX.getEpochSecond() + 1;
	int impossiblyBigNanos = 1_000_000_000;

	@Test
	public void mapsSensibleTimestamp() {
		// given:
		var proto = from(now.getEpochSecond(), now.getNano());

		// expect:
		assertEquals(now, PureValidation.asCoercedInstant(proto));
	}

	@Test
	public void coercesTooSmallTimestamp() {
		// given:
		var proto = from(impossiblySmallSecs, impossiblySmallNanos);

		// expect:
		assertEquals(Instant.MIN, PureValidation.asCoercedInstant(proto));
	}

	@Test
	public void coercesTooBigTimestamp() {
		// given:
		var proto = from(impossiblyBigSecs, impossiblyBigNanos);

		// expect:
		assertEquals(Instant.MAX, PureValidation.asCoercedInstant(proto));
	}

	public static Timestamp from(long secs, int nanos) {
		return Timestamp.newBuilder()
				.setSeconds(secs)
				.setNanos(nanos)
				.build();
	}
}
