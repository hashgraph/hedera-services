package com.hedera.services.stats;

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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.Platform;
import com.swirlds.platform.StatsRunningAverage;
import com.swirlds.platform.StatsSpeedometer;

public class MiscRunningAvgs {
	private final RunningAvgFactory runningAvg;

	StatsRunningAverage accountRetryWaitMs;
	StatsRunningAverage accountLookupRetries;
	StatsRunningAverage recordStreamQueueSize;
	StatsRunningAverage handledSubmitMessageSize;
	StatsRunningAverage hashQueueSize;

	public MiscRunningAvgs(RunningAvgFactory runningAvg, NodeLocalProperties properties) {
		this.runningAvg = runningAvg;

		double halfLife = properties.statsRunningAvgHalfLifeSecs();

		accountRetryWaitMs = new StatsRunningAverage(halfLife);
		accountLookupRetries = new StatsRunningAverage(halfLife);
		recordStreamQueueSize = new StatsRunningAverage(halfLife);
		handledSubmitMessageSize = new StatsRunningAverage(halfLife);
		hashQueueSize = new StatsRunningAverage(halfLife);
	}

	public void registerWith(Platform platform) {
		platform.addAppStatEntry(
				runningAvg.from(
						Names.ACCOUNT_LOOKUP_RETRIES,
						Descriptions.ACCOUNT_LOOKUP_RETRIES,
						accountLookupRetries));
		platform.addAppStatEntry(
				runningAvg.from(
						Names.ACCOUNT_RETRY_WAIT_MS,
						Descriptions.ACCOUNT_RETRY_WAIT_MS,
						accountRetryWaitMs));
		platform.addAppStatEntry(
				runningAvg.from(
						Names.RECORD_STREAM_QUEUE_SIZE,
						Descriptions.RECORD_STREAM_QUEUE_SIZE,
						recordStreamQueueSize));
		platform.addAppStatEntry(
				runningAvg.from(
						Names.HANDLED_SUBMIT_MESSAGE_SIZE,
						Descriptions.HANDLED_SUBMIT_MESSAGE_SIZE,
						handledSubmitMessageSize));
		platform.addAppStatEntry(
				runningAvg.from(
						Names.HASH_QUEUE_SIZE,
						Descriptions.HASH_QUEUE_SIZE,
						hashQueueSize
				)
		);
	}

	public void recordAccountLookupRetries(int num) {
		accountLookupRetries.recordValue(num);
	}

	public void recordAccountRetryWaitMs(double time) {
		accountRetryWaitMs.recordValue(time);
	}

	public void recordStreamQueueSize(int num) {
		recordStreamQueueSize.recordValue(num);
	}

	public void recordHandledSubmitMessageSize(int bytes) {
		handledSubmitMessageSize.recordValue(bytes);
	}

	public void hashQueueSize(int num) {
		hashQueueSize.recordValue(num);
	}

	static class Names {
		public static final String ACCOUNT_RETRY_WAIT_MS = "avgAcctRetryWaitMs";
		public static final String ACCOUNT_LOOKUP_RETRIES = "avgAcctLookupRetryAttempts";
		public static final String RECORD_STREAM_QUEUE_SIZE = "recordStreamQueueSize";
		public static final String HANDLED_SUBMIT_MESSAGE_SIZE = "avgHdlSubMsgSize";
		public static final String HASH_QUEUE_SIZE = "hashQueueSize";
	}

	static class Descriptions {
		public static final String ACCOUNT_RETRY_WAIT_MS =
				"average time is millis spent waiting to lookup the account number";
		public static final String ACCOUNT_LOOKUP_RETRIES =
				"average number of retry attempts made to lookup the account number";
		public static final String RECORD_STREAM_QUEUE_SIZE =
				"size of the queue from which we take records and write to RecordStream file";
		public static final String HANDLED_SUBMIT_MESSAGE_SIZE =
				"average size of the handled HCS submit message transaction";
		public static final String HASH_QUEUE_SIZE = "size of working queue for calculating hash and runningHash";
	}
}
