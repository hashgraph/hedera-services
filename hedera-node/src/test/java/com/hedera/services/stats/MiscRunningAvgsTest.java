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

import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import com.swirlds.platform.StatsRunningAverage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

class MiscRunningAvgsTest {
	double halfLife = 10.0;
	Platform platform;

	RunningAvgFactory factory;
	NodeLocalProperties properties;

	MiscRunningAvgs subject;

	@BeforeEach
	public void setup() throws Exception {
		factory = mock(RunningAvgFactory.class);
		platform = mock(Platform.class);

		properties = mock(NodeLocalProperties.class);
		given(properties.statsRunningAvgHalfLifeSecs()).willReturn(halfLife);

		subject = new MiscRunningAvgs(factory, properties);
	}

	@Test
	public void registersExpectedStatEntries() {
		// setup:
		StatEntry retries = mock(StatEntry.class);
		StatEntry waitMs = mock(StatEntry.class);
		StatEntry queueSizes = mock(StatEntry.class);
		StatEntry submitSizes = mock(StatEntry.class);

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

		// when:
		subject.registerWith(platform);

		// then:
		verify(platform).addAppStatEntry(retries);
		verify(platform).addAppStatEntry(waitMs);
		verify(platform).addAppStatEntry(queueSizes);
		verify(platform).addAppStatEntry(submitSizes);
	}

	@Test
	public void recordsToExpectedAvgs() {
		// setup:
		StatsRunningAverage retries = mock(StatsRunningAverage.class);
		StatsRunningAverage waitMs = mock(StatsRunningAverage.class);
		StatsRunningAverage queueSize = mock(StatsRunningAverage.class);
		StatsRunningAverage submitSizes = mock(StatsRunningAverage.class);
		// and:
		subject.accountLookupRetries = retries;
		subject.accountRetryWaitMs = waitMs;
		subject.handledSubmitMessageSize = submitSizes;
		subject.writeQueueSizeRecordStream = queueSize;

		// when:
		subject.recordAccountLookupRetries(1);
		subject.recordAccountRetryWaitMs(2.0);
		subject.recordHandledSubmitMessageSize(3);
		subject.writeQueueSizeRecordStream(4);

		// then:
		verify(retries).recordValue(1.0);
		verify(waitMs).recordValue(2.0);
		verify(submitSizes).recordValue(3.0);
		verify(queueSize).recordValue(4.0);
	}
}
