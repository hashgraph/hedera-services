package com.hedera.services.txns.validation;

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

import com.hedera.test.utils.TxnUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PureValidationTest {
	private static final Instant now = Instant.now();
	private static final long impossiblySmallSecs = Instant.MIN.getEpochSecond() - 1;
	private static final int impossiblySmallNanos = -1;
	private static final long impossiblyBigSecs = Instant.MAX.getEpochSecond() + 1;
	private static final int impossiblyBigNanos = 1_000_000_000;

	@Test
	void throwsInConstructor() {
		assertThrows(IllegalStateException.class, PureValidation::new);
	}

	@Test
	void mapsSensibleTimestamp() {
		final var proto = TxnUtils.timestampFrom(now.getEpochSecond(), now.getNano());

		assertEquals(now, PureValidation.asCoercedInstant(proto));
	}

	@Test
	void coercesTooSmallTimestamp() {
		final var proto = TxnUtils.timestampFrom(impossiblySmallSecs, impossiblySmallNanos);

		assertEquals(Instant.MIN, PureValidation.asCoercedInstant(proto));
	}

	@Test
	void coercesTooBigTimestamp() {
		final var proto = TxnUtils.timestampFrom(impossiblyBigSecs, impossiblyBigNanos);

		assertEquals(Instant.MAX, PureValidation.asCoercedInstant(proto));
	}
}
