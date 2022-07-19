/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import java.io.File;
import java.util.Queue;
import org.apache.commons.lang3.RandomUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(LogCaptureExtension.class)
public class RecordStreamManagerTest {
    private static final Platform platform = mock(Platform.class);

    private static final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);

    private static final long recordsLogPeriod = 5;
    private static final int recordStreamQueueCapacity = 100;
    private static final String baseLogDir = "recordStreamTest/";
    private static final String sidecarDir = "sidecarDir";
    private static final String recordMemo = "0.0.3";

    private static final String INITIALIZE_NOT_NULL =
            "after initialization, the instance should not be null";
    private static final String INITIALIZE_QUEUE_EMPTY =
            "after initialization, hash queue should be empty";
    private static final String UNEXPECTED_VALUE = "unexpected value";

    private static RecordStreamManager disableStreamingInstance;
    private static RecordStreamManager enableV5StreamingInstance;
    private static RecordStreamManager enableV6StreamingInstance;

    public static final Hash INITIAL_RANDOM_HASH =
            new Hash(RandomUtils.nextBytes(DigestType.SHA_384.digestLength()));

    private static final MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
    private static final QueueThreadObjectStream<RecordStreamObject> writeQueueThreadMock =
            mock(QueueThreadObjectStream.class);
    private static final GlobalDynamicProperties globalDynamicProperties =
            mock(GlobalDynamicProperties.class);
    private static final RecordStreamManager RECORD_STREAM_MANAGER =
            new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);

    @LoggingTarget private LogCaptor logCaptor;
    @LoggingSubject private RecordStreamManager recordStreamManager;

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

        disableStreamingInstance =
                new RecordStreamManager(
                        platform,
                        runningAvgsMock,
                        disabledProps,
                        recordMemo,
                        INITIAL_RANDOM_HASH,
                        streamType,
                        globalDynamicProperties);

        given(globalDynamicProperties.recordFileVersion()).willReturn(5);
        enableV5StreamingInstance =
                new RecordStreamManager(
                        platform,
                        runningAvgsMock,
                        enabledProps,
                        recordMemo,
                        INITIAL_RANDOM_HASH,
                        streamType,
                        globalDynamicProperties);

        given(globalDynamicProperties.recordFileVersion()).willReturn(6);
        enableV6StreamingInstance =
                new RecordStreamManager(
                        platform,
                        runningAvgsMock,
                        enabledProps,
                        recordMemo,
                        INITIAL_RANDOM_HASH,
                        streamType,
                        globalDynamicProperties);
    }

    private static void configProps(NodeLocalProperties props) {
        given(props.recordLogDir()).willReturn(baseLogDir);
        given(props.recordLogPeriod()).willReturn(recordsLogPeriod);
        given(props.recordStreamQueueCapacity()).willReturn(recordStreamQueueCapacity);
        given(props.sidecarDir()).willReturn(sidecarDir);
    }

    @Test
    void initializeTest() {
        assertNull(
                disableStreamingInstance.getV5StreamFileWriter(),
                "When recordStreaming is disabled, V5streamFileWriter instance should be null");
        assertNull(
                disableStreamingInstance.getProtobufStreamFileWriter(),
                "When recordStreaming is disabled, V6streamFileWriter instance should be null");
        assertNotNull(disableStreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
        assertNotNull(disableStreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
        assertEquals(0, disableStreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
        assertEquals(0, disableStreamingInstance.getWriteQueueSize(), INITIALIZE_QUEUE_EMPTY);

        assertNotNull(
                enableV5StreamingInstance.getV5StreamFileWriter(),
                "When V5 recordStreaming is enabled, V5streamFileWriter instance should not be"
                        + " null");
        assertNull(
                enableV5StreamingInstance.getProtobufStreamFileWriter(),
                "When V5 recordStreaming is enabled, V6streamFileWriter instance should be null");
        assertNotNull(enableV5StreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
        assertNotNull(enableV5StreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
        assertEquals(0, enableV5StreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
        assertEquals(0, enableV5StreamingInstance.getWriteQueueSize(), INITIALIZE_QUEUE_EMPTY);

        assertNull(
                enableV6StreamingInstance.getV5StreamFileWriter(),
                "When V6 recordStreaming is enabled, V5streamFileWriter instance should be null");
        assertNotNull(
                enableV6StreamingInstance.getProtobufStreamFileWriter(),
                "When V6 recordStreaming is enabled, V6streamFileWriter instance should not be"
                        + " null");
        assertNotNull(enableV6StreamingInstance.getMultiStream(), INITIALIZE_NOT_NULL);
        assertNotNull(enableV6StreamingInstance.getHashCalculator(), INITIALIZE_NOT_NULL);
        assertEquals(0, enableV6StreamingInstance.getHashQueueSize(), INITIALIZE_QUEUE_EMPTY);
        assertEquals(0, enableV6StreamingInstance.getWriteQueueSize(), INITIALIZE_QUEUE_EMPTY);
    }

    @Test
    void setInitialHashTest() {
        RECORD_STREAM_MANAGER.setInitialHash(INITIAL_RANDOM_HASH);
        verify(multiStreamMock).setRunningHash(INITIAL_RANDOM_HASH);
        assertEquals(
                INITIAL_RANDOM_HASH,
                RECORD_STREAM_MANAGER.getInitialHash(),
                "initialHash is not set");
    }

    @Test
    void warnsOnInterruptedStreaming() {
        // setup:
        final var mockQueue = mock(Queue.class);

        given(writeQueueThreadMock.getQueue()).willReturn(mockQueue);
        recordStreamManager =
                new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);

        willThrow(RuntimeException.class).given(multiStreamMock).addObject(any());

        // when:
        recordStreamManager.addRecordStreamObject(new RecordStreamObject());

        // then:
        assertThat(
                logCaptor.warnLogs(),
                contains(Matchers.startsWith("Unhandled exception while streaming")));
    }

    @Test
    void addRecordStreamObjectTest() {
        // setup:
        final MiscRunningAvgs runningAvgsMock = mock(MiscRunningAvgs.class);
        final var mockQueue = mock(Queue.class);
        recordStreamManager =
                new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);
        assertFalse(
                recordStreamManager.getInFreeze(), "inFreeze should be false after initialization");
        final int recordsNum = 10;
        for (int i = 1; i <= recordsNum; i++) {
            addRecordStreamObject(runningAvgsMock, mockQueue, i, INITIAL_RANDOM_HASH);
        }
        // set inFreeze to be true
        recordStreamManager.setInFreeze(true);
        assertTrue(recordStreamManager.getInFreeze(), "inFreeze should be true");
        // add an object after inFreeze is true
        RecordStreamObject objectAfterFreeze = mock(RecordStreamObject.class);

        given(mockQueue.size()).willReturn(recordsNum);
        when(writeQueueThreadMock.getQueue()).thenReturn(mockQueue);

        recordStreamManager.addRecordStreamObject(objectAfterFreeze);
        // after frozen, when adding object to the RecordStreamManager, multiStream.add(object)
        // should not be called
        verify(multiStreamMock, never()).addObject(objectAfterFreeze);
        // multiStreamMock should be closed when inFreeze is set to be true
        verify(multiStreamMock).close();
        // should get recordStream queue size and set to runningAvgs
        verify(runningAvgsMock, times(2)).writeQueueSizeRecordStream(recordsNum);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setStartWriteAtCompleteWindowTest(boolean startWriteAtCompleteWindow) {
        // when
        enableV5StreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);

        // then
        assertEquals(
                startWriteAtCompleteWindow,
                enableV5StreamingInstance.getV5StreamFileWriter().getStartWriteAtCompleteWindow(),
                UNEXPECTED_VALUE);
        assertThat(
                logCaptor.infoLogs(),
                contains(
                        Matchers.startsWith("RecordStreamManager::setStartWriteAtCompleteWindow")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setStartWriteAtCompleteWindowTestV6(boolean startWriteAtCompleteWindow) {
        // when
        enableV6StreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);

        // then
        assertEquals(
                startWriteAtCompleteWindow,
                enableV6StreamingInstance
                        .getProtobufStreamFileWriter()
                        .getStartWriteAtCompleteWindow(),
                UNEXPECTED_VALUE);
        assertThat(
                logCaptor.infoLogs(),
                contains(
                        Matchers.startsWith("RecordStreamManager::setStartWriteAtCompleteWindow")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setStartWriteAtCompleteWindowTestWhenRecordStreamingIsDisabledDoesNothingSilently(
            boolean startWriteAtCompleteWindow) {
        // when
        disableStreamingInstance.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);

        // then
        assertTrue(logCaptor.infoLogs().isEmpty());
    }

    @Test
    void setInFreezeTest() {
        MultiStream<RecordStreamObject> multiStreamMock = mock(MultiStream.class);
        recordStreamManager =
                new RecordStreamManager(multiStreamMock, writeQueueThreadMock, runningAvgsMock);

        recordStreamManager.setInFreeze(false);
        assertFalse(recordStreamManager.getInFreeze());

        recordStreamManager.setInFreeze(true);

        assertTrue(recordStreamManager.getInFreeze());
        // multiStreamMock should be closed when inFreeze is true;
        verify(multiStreamMock).close();
        // and:
        assertThat(
                logCaptor.infoLogs(),
                contains(
                        "RecordStream inFreeze is set to be false",
                        "RecordStream inFreeze is set to be true"));
    }

    @Test
    void computesExpectedLogDirs() {
        final var memo = "0.0.3";
        final var withoutSeparatorSuffix = "somewhere/else";
        final var withSeparatorSuffix = "somewhere/else/";
        final var expected = "somewhere/else/record0.0.3";

        assertEquals(expected, RecordStreamManager.effectiveLogDir(withSeparatorSuffix, memo));
        assertEquals(expected, RecordStreamManager.effectiveLogDir(withoutSeparatorSuffix, memo));
    }

    @Test
    void computesExpectedSidecarLogDirs() {
        final var withoutSeparatorSuffix = "somewhere/else";
        final var withSeparatorSuffix = "somewhere/else/";

        var expected = withoutSeparatorSuffix + File.separator;
        var actual = RecordStreamManager.effectiveSidecarDir(withoutSeparatorSuffix, "");
        assertEquals(expected, actual);

        expected = withSeparatorSuffix;
        actual = RecordStreamManager.effectiveSidecarDir(withSeparatorSuffix, "");
        assertEquals(expected, actual);

        final var sidecarFolder = "sidecars";
        expected = withoutSeparatorSuffix + File.separator + sidecarFolder;
        actual = RecordStreamManager.effectiveSidecarDir(withoutSeparatorSuffix, sidecarFolder);
        assertEquals(expected, actual);

        expected = withSeparatorSuffix + sidecarFolder;
        actual = RecordStreamManager.effectiveSidecarDir(withSeparatorSuffix, sidecarFolder);
        assertEquals(expected, actual);
    }

    // For ease of testing, we will assume that a new block contains a single RecordStreamObject.
    // In the real world scenario, a block/record file will contain >=1 RecordStreamObjects.
    private void addRecordStreamObject(
            final MiscRunningAvgs runningAvgsMock,
            final Queue mockQueue,
            final int queueSize,
            final Hash hash) {
        final RecordStreamObject recordStreamObject = mock(RecordStreamObject.class);
        when(writeQueueThreadMock.getQueue()).thenReturn(mockQueue);
        given(mockQueue.size()).willReturn(queueSize);
        when(recordStreamObject.getRunningHash()).thenReturn(new RunningHash(hash));
        recordStreamManager.addRecordStreamObject(recordStreamObject);
        verify(multiStreamMock).addObject(recordStreamObject);
        verify(runningAvgsMock).writeQueueSizeRecordStream(queueSize);
        // multiStream should not be closed after adding it
        verify(multiStreamMock, never()).close();
        assertFalse(
                recordStreamManager.getInFreeze(),
                "inFreeze should be false after adding the records");
    }
}
