package com.hedera.services.stream;

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
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import org.apache.commons.lang3.RandomUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Queue;

import static com.hedera.services.state.merkle.MerkleNetworkContext.NULL_CONSENSUS_TIME;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.*;

@ExtendWith(LogCaptureExtension.class)
public class RecordStreamManagerTest {
	private static final Platform platform = mock(Platform.class);

	private static final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);

	private static final long recordsLogPeriod = 5;
	private static final int recordStreamQueueCapacity = 100;
	private static final String baseLogDir = "recordStreamTest/";
	private static final String recordMemo = "0.0.3";

	private static final String INITIALIZE_NOT_NULL = "after initialization, the instance should not be null";
	private static final String INITIALIZE_QUEUE_EMPTY = "after initialization, hash queue should be empty";
	private static final String UNEXPECTED_VALUE = "unexpected value";

	private static RecordStreamManager disableStreamingInstance;
	private static RecordStreamManager enableStreamingInstance;

	public static final Hash INITIAL_RANDOM_HASH = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));

	private static final MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
	private static final QueueThreadObjectStream<RecordStreamObject> writeQueueThreadMock =
			mock(QueueThreadObjectStream.class);
	private static final RecordStreamManager RECORD_STREAM_MANAGER = new RecordStreamManager(
			multiStreamMock, writeQueueThreadMock, runningAvgsMock);

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private RecordStreamManager recordStreamManager;

	@BeforeAll
	public static void init() throws Exception {
		given(platform.getSelfId()).willReturn(new NodeId(false, 0L));
		NodeLocalProperties disabledProps = mock(NodeLocalProperties.class);
		given(disabledProps.isRecordStreamEnabled()).willReturn(false);
		NodeLocalProperties enabledProps = mock(NodeLocalProperties.class);
		given(enabledProps.isRecordStreamEnabled()).willReturn(true);
		configProps(disabledProps);
		configProps(enabledProps);
		RecordStreamType streamType = mock(RecordStreamType.class);

		disableStreamingInstance = new RecordStreamManager(
				platform,
				runningAvgsMock,
				disabledProps,
				recordMemo,
				INITIAL_RANDOM_HASH,
				streamType);
		enableStreamingInstance = new RecordStreamManager(
				platform,
				runningAvgsMock,
				enabledProps,
				recordMemo,
				INITIAL_RANDOM_HASH,
				streamType);
	}

	private static void configProps(NodeLocalProperties props) {
		given(props.recordLogDir()).willReturn(baseLogDir);
		given(props.recordLogPeriod()).willReturn(recordsLogPeriod);
		given(props.recordStreamQueueCapacity()).willReturn(recordStreamQueueCapacity);
	}

	@Test
	void initializeTest() {
		assertNull(disableStreamingInstance.getStreamFileWriter(),
				"When recordStreaming is disabled, streamFileWriter instance should be null");
		assertNotNull(disableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
		assertNotNull(disableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
		assertEquals(0, disableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
		assertEquals(0, disableStreamingInstance.getWriteQueueSize(), INITIALIZE_QUEUE_EMPTY);

		assertNotNull(enableStreamingInstance.getStreamFileWriter(),
				"When recordStreaming is enabled, streamFileWriter instance should not be null");
		assertNotNull(enableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
		assertNotNull(enableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
		assertEquals(0, enableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
		assertEquals(0, enableStreamingInstance.getWriteQueueSize(), INITIALIZE_QUEUE_EMPTY);
	}

	@Test
	void setInitialHashTest() {
		RECORD_STREAM_MANAGER.setInitialHash(INITIAL_RANDOM_HASH);
		verify(multiStreamMock).setRunningHash(INITIAL_RANDOM_HASH);
		assertEquals(INITIAL_RANDOM_HASH, RECORD_STREAM_MANAGER.getInitialHash(), "initialHash is not set");
	}

	@Test
	void warnsOnInterruptedStreaming() {
		// setup:
		final var mockQueue = mock(Queue.class);

		given(writeQueueThreadMock.getQueue()).willReturn(mockQueue);
		recordStreamManager = new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);

		willThrow(RuntimeException.class).given(multiStreamMock).addObject(any());

		// when:
		recordStreamManager.addRecordStreamObject(new RecordStreamObject());

		// then:
		assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith("Unhandled exception while streaming")));
	}

	@Test
	void addRecordStreamObjectTest() {
		// setup:
		final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);
		final MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext(
				NULL_CONSENSUS_TIME,
				new SequenceNumber(2),
				1,
				new ExchangeRates());
		final var mockQueue = mock(Queue.class);
		recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after initialization");
		final int recordsNum = 10;
		for (int i = 1; i <= recordsNum; i++) {
			addRecordStreamObject(runningAvgsMock, mockQueue, i, INITIAL_RANDOM_HASH);
		}
		// set inFreeze to be true
		recordStreamManager.setInFreeze(true);
		assertTrue(recordStreamManager.getInFreeze(),
				"inFreeze should be true");
		// add an object after inFreeze is true
		RecordStreamObject objectAfterFreeze = mock(RecordStreamObject.class);

		given(mockQueue.size()).willReturn(recordsNum);
		when(writeQueueThreadMock.getQueue()).thenReturn(mockQueue);

		recordStreamManager.addRecordStreamObject(objectAfterFreeze);
		// after frozen, when adding object to the RecordStreamManager, multiStream.add(object) should not be called
		verify(multiStreamMock, never()).addObject(objectAfterFreeze);
		// multiStreamMock should be closed when inFreeze is set to be true
		verify(multiStreamMock).close();
		// should get recordStream queue size and set to runningAvgs
		verify(runningAvgsMock, times(2)).writeQueueSizeRecordStream(recordsNum);
	}

	@Test
	void testMaxBlockHashCache() {
		// setup:
		final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);
		final MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext(
				NULL_CONSENSUS_TIME,
				new SequenceNumber(2),
				1,
				new ExchangeRates());
		final var mockQueue = mock(Queue.class);
		recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after initialization");
		final int recordsNum = 256;

		for (int i = 1; i <= recordsNum; i++) {
			addRecordStreamObject(runningAvgsMock, mockQueue, i, INITIAL_RANDOM_HASH);
		}
		assertEquals(new Hash(), merkleNetworkContext.getBlockHashCache(1));
		assertEquals(INITIAL_RANDOM_HASH, merkleNetworkContext.getBlockHashCache(256));
		assertEquals(256, merkleNetworkContext.getBlockHashCache().size());
		assertEquals(recordsNum, merkleNetworkContext.getBlockNo());
	}

	@Test
	void testBlockHashCacheIsBeingOverwrittenAfterMaxLimit() {
		// setup:
		final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);
		final MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext(
				NULL_CONSENSUS_TIME,
				new SequenceNumber(2),
				1,
				new ExchangeRates());
		final var mockQueue = mock(Queue.class);
		recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after initialization");

		final Hash OVERWRITING_HASH = new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));
		for (int i = 1; i <= 255; i++) {
			addRecordStreamObject(runningAvgsMock, mockQueue, i, INITIAL_RANDOM_HASH);
		}
		addRecordStreamObject(runningAvgsMock, mockQueue, 256, OVERWRITING_HASH);
		addRecordStreamObject(runningAvgsMock, mockQueue, 257, OVERWRITING_HASH);

		assertEquals(OVERWRITING_HASH, merkleNetworkContext.getBlockHashCache(257));
		assertEquals(OVERWRITING_HASH, merkleNetworkContext.getPrevStreamedRecordHash());
		assertEquals(256, merkleNetworkContext.getBlockHashCache().size());
		assertEquals(257, merkleNetworkContext.getBlockNo());

		//Check blockNo and block hash of all block hash entries without the last one
		int i = 2;
		for (final Map.Entry<Long, Hash> blockHashEntry : merkleNetworkContext.getBlockHashCache().entrySet()) {
			if (i != 257) {
				assertEquals(i, blockHashEntry.getKey());
				assertEquals(INITIAL_RANDOM_HASH, blockHashEntry.getValue());
			}
			i++;
		}
	}

	@Test
	void testExceedingMaxBlockHashCache() {
		// setup:
		final MerkleNetworkContext merkleNetworkContext = new MerkleNetworkContext(
				NULL_CONSENSUS_TIME,
				new SequenceNumber(2),
				1,
				new ExchangeRates());
		final var mockQueue = mock(Queue.class);
		recordStreamManager = new RecordStreamManager(
				multiStreamMock, writeQueueThreadMock, runningAvgsMock);
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after initialization");
		final int recordsNum = 1234;

		for (int i = 1; i <= recordsNum; i++) {
			addRecordStreamObject(runningAvgsMock, mockQueue, i, INITIAL_RANDOM_HASH);
		}

		assertEquals(new Hash(), merkleNetworkContext.getBlockHashCache(1));
		assertEquals(INITIAL_RANDOM_HASH, merkleNetworkContext.getBlockHashCache(1234));
		assertEquals(256, merkleNetworkContext.getBlockHashCache().size());
		assertEquals(recordsNum, merkleNetworkContext.getBlockNo());
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void setStartWriteAtCompleteWindowTest(boolean startWriteAtCompleteWindow) {
		enableStreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
		assertEquals(startWriteAtCompleteWindow,
				enableStreamingInstance.getStreamFileWriter().getStartWriteAtCompleteWindow(), UNEXPECTED_VALUE);
	}

	@Test
	void setInFreezeTest() {
		MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
		recordStreamManager = new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);

		recordStreamManager.setInFreeze(false);
		assertFalse(recordStreamManager.getInFreeze());

		recordStreamManager.setInFreeze(true);

		assertTrue(recordStreamManager.getInFreeze());
		// multiStreamMock should be closed when inFreeze is true;
		verify(multiStreamMock).close();
		// and:
		assertThat(logCaptor.infoLogs(), contains(
				"RecordStream inFreeze is set to be false",
				"RecordStream inFreeze is set to be true"));
	}

	@Test
	void computesExpectedLogDirs() {
		final var memo = "0.0.3";
		final var withoutSeparatorSuffix = "somewhere/else";
		final var withSeparatorSuffix = "somewhere/else/";
		final var expected = "somewhere/else/record0.0.3";

		assertEquals(expected, RecordStreamManager.effLogDir(withSeparatorSuffix, memo));
		assertEquals(expected, RecordStreamManager.effLogDir(withoutSeparatorSuffix, memo));
	}

	// For ease of testing, we will assume that a new block contains a single RecordStreamObject.
	// In the real world scenario, a block/record file will contain >=1 RecordStreamObjects.
	private void addRecordStreamObject(final MiscRunningAvgs runningAvgsMock,
													 final Queue mockQueue, final int queueSize, final Hash hash) {
		final RecordStreamObject recordStreamObject = mock(RecordStreamObject.class);
		when(writeQueueThreadMock.getQueue()).thenReturn(mockQueue);
		given(mockQueue.size()).willReturn(queueSize);
		when(recordStreamObject.getRunningHash()).thenReturn(new RunningHash(hash));
		recordStreamManager.addRecordStreamObject(recordStreamObject);
		verify(multiStreamMock).addObject(recordStreamObject);
		verify(runningAvgsMock).writeQueueSizeRecordStream(queueSize);
		// multiStream should not be closed after adding it
		verify(multiStreamMock, never()).close();
		assertFalse(recordStreamManager.getInFreeze(),
				"inFreeze should be false after adding the records");
	}
}