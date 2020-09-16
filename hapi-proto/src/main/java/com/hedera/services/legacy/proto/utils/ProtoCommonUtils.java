package com.hedera.services.legacy.proto.utils;

/*-
 * ‌
 * Hedera Services API
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

import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.builder.RequestBuilder;

import java.time.Clock;
import java.time.Instant;

/**
 * Protobuf related utilities shared by client and server.
 *
 * @author hua Created on 2018-09-29
 */
public class ProtoCommonUtils {

	/**
	 * Creates a file ID instance with given info.
	 *
	 * @return generated file ID instance
	 */
	public static FileID createFileID(long fileNum, long realmNum, long shardNum) {
		FileID fid = FileID.newBuilder()
				.setFileNum(fileNum)
				.setRealmNum(realmNum)
				.setShardNum(shardNum).build();
		return fid;
	}

	/**
	 * Gets current UTC timestamp.
	 *
	 * @param sec_to_wind
	 * 		seconds to wind the clock. If negative, wind back, else wind forward.
	 * @return the timestamp
	 */
	public static Timestamp getCurrentTimestampUTC(long sec_to_wind) {
		Timestamp timestamp = null;
		timestamp = RequestBuilder
				.getTimestamp(Instant.now(Clock.systemUTC()).plusSeconds(sec_to_wind));
		return timestamp;
	}

	/**
	 * Gets the current UTC instant.
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
	public static Timestamp addSecondsToTimestamp(Timestamp timestamp, long seconds) {
		return Timestamp.newBuilder().setNanos(timestamp.getNanos())
				.setSeconds(timestamp.getSeconds() + seconds).build();
	}
}
