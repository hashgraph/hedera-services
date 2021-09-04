package com.hedera.services.legacy.proto.utils;

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

import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.builder.RequestBuilder;

import java.time.Clock;
import java.time.Instant;

/**
 * Protobuf related utilities shared by client and server.
 */
public final class ProtoCommonUtils {
	private ProtoCommonUtils() {
		throw new UnsupportedOperationException("Utility Class");
	}

	/**
	 * Gets current UTC timestamp.
	 *
	 * @param secToWind
	 * 		seconds to wind the clock. If negative, wind back, else wind forward.
	 * @return the timestamp
	 */
	public static Timestamp getCurrentTimestampUTC(final long secToWind) {
		return RequestBuilder.getTimestamp(getCurrentInstantUTC().plusSeconds(secToWind));
	}

	/**
	 * Gets the current UTC instant.
	 *
	 * @return current UTC instant
	 */
	public static Instant getCurrentInstantUTC() {
		return Instant.now(Clock.systemUTC());
	}

	/**
	 * Adds a number of seconds to a timestamp
	 *
	 * @param timestamp
	 * 		timestamp to which to add seconds
	 * @param seconds
	 * 		number of seconds to add
	 * @return the timestamp plus the number of seconds
	 */
	public static Timestamp addSecondsToTimestamp(final Timestamp timestamp, final long seconds) {
		return timestamp.toBuilder()
				.setSeconds(timestamp.getSeconds() + seconds)
				.build();
	}
}
