package com.hederahashgraph.builder;

/*-
 * ‌
 * Hedera Services API Utilities
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

class RequestBuilderTest {

	@Test
	void testExpirationTime() {
		final var seconds = 500L;
		final var duration = RequestBuilder.getDuration(seconds);
		final var now = Instant.now();

		final var expirationTime = RequestBuilder.getExpirationTime(now, duration);
		Assertions.assertNotNull(expirationTime);

		final var expirationInstant = RequestBuilder.convertProtoTimeStamp(expirationTime);
		final var between = Duration.between(now, expirationInstant);
		Assertions.assertEquals(seconds, between.getSeconds());
	}
}
