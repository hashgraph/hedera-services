package com.hedera.services.stats;

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

import com.swirlds.common.Platform;
import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsRunningAverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class MiscRunningAvgsTest {
	private static final double halfLife = 10.0;

	private Platform platform;
	private RunningAvgFactory factory;

	private MiscRunningAvgs subject;

	@BeforeEach
	void setup() throws Exception {
		factory = mock(RunningAvgFactory.class);
		platform = mock(Platform.class);

		subject = new MiscRunningAvgs(factory, halfLife);
	}

	@Test
	void registersExpectedStatEntries() {
		final var retries = mock(StatEntry.class);
		final var waitMs = mock(StatEntry.class);
		final var queueSizes = mock(StatEntry.class);
		final var submitSizes = mock(StatEntry.class);
		given(factory.from(
				argThat(MiscRunningAvgs.Names.ACCOUNT_LOOKUP_RETRIES::equals),
				argThat(MiscRunningAvgs.Descriptions.ACCOUNT_LOOKUP_RETRIES::equals),
				argThat(subject.accountLookupRetries::equals))).willReturn(retries);
		given(factory.from(
				argThat(MiscRunningAvgs.Names.ACCOUNT_RETRY_WAIT_MS::equals),
				argThat(MiscRunningAvgs.Descriptions.ACCOUNT_RETRY_WAIT_MS::equals),
				argThat(subject.accountRetryWaitMs::equals))).willReturn(waitMs);
		given(factory.from(
				argThat(MiscRunningAvgs.Names.WRITE_QUEUE_SIZE_RECORD_STREAM::equals),
				argThat(MiscRunningAvgs.Descriptions.WRITE_QUEUE_SIZE_RECORD_STREAM::equals),
				argThat(subject.writeQueueSizeRecordStream::equals))).willReturn(queueSizes);
		given(factory.from(
				argThat(MiscRunningAvgs.Names.HANDLED_SUBMIT_MESSAGE_SIZE::equals),
				argThat(MiscRunningAvgs.Descriptions.HANDLED_SUBMIT_MESSAGE_SIZE::equals),
				argThat(subject.handledSubmitMessageSize::equals))).willReturn(submitSizes);

		subject.registerWith(platform);

		verify(platform).addAppStatEntry(retries);
		verify(platform).addAppStatEntry(waitMs);
		verify(platform).addAppStatEntry(queueSizes);
		verify(platform).addAppStatEntry(submitSizes);
	}

	@Test
	void recordsToExpectedAvgs() {
		final var retries = mock(StatsRunningAverage.class);
		final var waitMs = mock(StatsRunningAverage.class);
		final var queueSize = mock(StatsRunningAverage.class);
		final var submitSizes = mock(StatsRunningAverage.class);
		final var hashS = mock(StatsRunningAverage.class);
		subject.accountLookupRetries = retries;
		subject.accountRetryWaitMs = waitMs;
		subject.handledSubmitMessageSize = submitSizes;
		subject.writeQueueSizeRecordStream = queueSize;
		subject.hashQueueSizeRecordStream = hashS;

		subject.recordAccountLookupRetries(1);
		subject.recordAccountRetryWaitMs(2.0);
		subject.recordHandledSubmitMessageSize(3);
		subject.writeQueueSizeRecordStream(4);
		subject.hashQueueSizeRecordStream(5);

		verify(retries).recordValue(1.0);
		verify(waitMs).recordValue(2.0);
		verify(submitSizes).recordValue(3.0);
		verify(queueSize).recordValue(4.0);
		verify(hashS).recordValue(5);
	}
}
