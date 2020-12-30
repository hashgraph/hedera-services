package com.hedera.services.stream;

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

import com.hedera.services.stats.MiscRunningAvgs;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThread;
import org.apache.commons.lang3.RandomUtils;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RecordStreamManagerTest {
	private static final Platform platform = mock(Platform.class);
	private static final Logger mockLog = mock(Logger.class);

	private static final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);

	private static final long recordsLogPeriod = 5;
	private static final int recordStreamQueueCapacity = 100;
	private static final String recordStreamDir = "recordStreamTest/record0.0.3";

	private static final String INITIALIZE_NOT_NULL = "after initialization, the instance should not be null";
	private static final String INITIALIZE_QUEUE_EMPTY = "after initialization, hash queue should be empty";
	private static final String UNEXPECTED_VALUE = "unexpected value";

	private static RecordStreamManager disableStreamingInstance;
	private static RecordStreamManager enableStreamingInstance;

	public static final Hash INITIAL_RANDOM_HASH = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));

	private static final MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
	private static final QueueThread<RecordStreamObject> writeQueueThreadMock = mock(QueueThread.class);
	private static final RecordStreamManager RECORD_STREAM_MANAGER = new RecordStreamManager(
			multiStreamMock, writeQueueThreadMock, runningAvgsMock);

	@BeforeAll
	public static void init() throws Exception {
		disableStreamingInstance = new RecordStreamManager(platform, runningAvgsMock, false, recordStreamDir,
				recordsLogPeriod, recordStreamQueueCapacity, INITIAL_RANDOM_HASH);
		enableStreamingInstance = new RecordStreamManager(platform, runningAvgsMock, true, recordStreamDir,
				recordsLogPeriod, recordStreamQueueCapacity, INITIAL_RANDOM_HASH);
	}

	@Test
	public void initializeTest() {
		assertNull(disableStreamingInstance.getStreamFileWriter(),
				"When recordStreaming is disabled, streamFileWriter instance should be null");
		assertNotNull(disableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
		assertNotNull(disableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
		assertEquals(0, disableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
		assertEquals(0, disableStreamingInstance.getRecordStreamingQueueSize(), INITIALIZE_QUEUE_EMPTY);

		assertNotNull(enableStreamingInstance.getStreamFileWriter(),
				"When recordStreaming is enabled, streamFileWriter instance should not be null");
		assertNotNull(enableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
		assertNotNull(enableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
		assertEquals(0, enableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
		assertEquals(0, enableStreamingInstance.getRecordStreamingQueueSize(), INITIALIZE_QUEUE_EMPTY);
	}

	@Test
	public void setInitialHashTest() {
		RECORD_STREAM_MANAGER.setInitialHash(INITIAL_RANDOM_HASH);
		verify(multiStreamMock).setRunningHash(INITIAL_RANDOM_HASH);
		assertEquals(INITIAL_RANDOM_HASH, RECORD_STREAM_MANAGER.getInitialHash(), "initialHash is not set");
	}

	@Test
	public void addRecordStreamObjectTest() throws InterruptedException {
		RecordStreamManager recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after initialization");
		final int recordsNum = 10;
		for (int i = 0; i < recordsNum; i++) {
			RecordStreamObject recordStreamObject = mock(RecordStreamObject.class);
			when(writeQueueThreadMock.getQueueSize()).thenReturn(i);
			recordStreamManager.addRecordStreamObject(recordStreamObject);
			verify(multiStreamMock).add(recordStreamObject);
			verify(runningAvgsMock).recordStreamQueueSize(i);
			// multiStream should not be closed after adding it
			verify(multiStreamMock, never()).close();
			assertFalse(recordStreamManager.getInFreeze(),
					"inFreeze should be false after adding the records");
		}
		// set inFreeze to be true
		recordStreamManager.setInFreeze(true);
		assertTrue(recordStreamManager.getInFreeze(),
				"inFreeze should be true");
		// add an object after inFreeze is true
		RecordStreamObject objectAfterFreeze = mock(RecordStreamObject.class);

		when(writeQueueThreadMock.getQueueSize()).thenReturn(recordsNum);

		recordStreamManager.addRecordStreamObject(objectAfterFreeze);
		// after frozen, when adding object to the RecordStreamManager, multiStream.add(object) should not be called
		verify(multiStreamMock, never()).add(objectAfterFreeze);
		// multiStream should be closed when inFreeze is set to be true
		verify(multiStreamMock).close();
		// should get recordStream queue size and set to runningAvgs
		verify(runningAvgsMock).recordStreamQueueSize(recordsNum);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void setStartWriteAtCompleteWindowTest(boolean startWriteAtCompleteWindow) {
		enableStreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
		assertEquals(startWriteAtCompleteWindow,
				enableStreamingInstance.getStreamFileWriter().getStartWriteAtCompleteWindow(), UNEXPECTED_VALUE);
	}

	@Test
	public void setInFreezeTest() {
		MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
		RecordStreamManager recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		RecordStreamManager.LOGGER = mockLog;

		recordStreamManager.setInFreeze(false);
		verify(mockLog).info("RecordStream inFreeze is set to be {} ", false);
		assertFalse(recordStreamManager.getInFreeze());

		recordStreamManager.setInFreeze(true);

		verify(mockLog).info("RecordStream inFreeze is set to be {} ", true);
		assertTrue(recordStreamManager.getInFreeze());
		// multiStream should be closed when inFreeze is true;
		verify(multiStreamMock).close();
	}
}
