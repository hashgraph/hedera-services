/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.stream;

import static com.swirlds.common.stream.MultiStream.NEXT_STREAM_NULL;
import static com.swirlds.common.stream.MultiStream.NOT_ENOUGH_NEXT_STREAMS;
import static com.swirlds.common.test.stream.HashCalculatorTest.PAY_LOAD_SIZE_4;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThreadObjectStream;
import com.swirlds.common.stream.internal.LinkedObjectStream;
import com.swirlds.common.test.RandomUtils;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MultiStreamTest {
    private static LinkedObjectStream<ObjectForTestStream> hashCalculator;
    private static LinkedObjectStream<ObjectForTestStream> queueThread;
    private static LinkedObjectStream<ObjectForTestStream> multiStream;

    @BeforeAll
    static void init() {
        hashCalculator = mock(HashCalculatorForStream.class);
        queueThread = mock(QueueThreadObjectStream.class);
        List<LinkedObjectStream<ObjectForTestStream>> nextStreams = new ArrayList<>();
        nextStreams.add(hashCalculator);
        nextStreams.add(queueThread);
        multiStream = new MultiStream<>(nextStreams);
    }

    @Test
    void addTest() throws InterruptedException {
        ObjectForTestStream object = ObjectForTestStream.getRandomObjectForTestStream(PAY_LOAD_SIZE_4);
        multiStream.addObject(object);
        verify(hashCalculator).addObject(object);
        verify(queueThread).addObject(object);
    }

    @Test
    void clearTest() throws InterruptedException {
        multiStream.clear();
        verify(hashCalculator).clear();
        verify(queueThread).clear();
    }

    @Test
    void closeTest() {
        multiStream.close();
        verify(hashCalculator).close();
        verify(queueThread).close();
    }

    @Test
    void setHashTest() {
        Hash hash = RandomUtils.randomHash();
        multiStream.setRunningHash(hash);
        verify(hashCalculator).setRunningHash(hash);
        verify(queueThread).setRunningHash(hash);
    }

    @Test
    void addNullNextStreamsTest() {
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MultiStream<>(null),
                "should throw exception when setting null nextStreams to multiStream");
        assertTrue(exception.getMessage().contains(NOT_ENOUGH_NEXT_STREAMS), "nextStreams should not be null");
    }

    @Test
    void addZeroNextStreamTest() {
        List<LinkedObjectStream<ObjectForTestStream>> nextStreams = new ArrayList<>();
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MultiStream<>(nextStreams),
                "should throw exception when setting nextStreams with zero element to multiStream");
        assertTrue(
                exception.getMessage().contains(NOT_ENOUGH_NEXT_STREAMS), "nextStreams should have at least 1 element");
    }

    @Test
    void addNullNextStreamTest() {
        List<LinkedObjectStream<ObjectForTestStream>> nextStreams = new ArrayList<>(2);
        nextStreams.add(null);
        nextStreams.add(mock(LinkedObjectStream.class));
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> new MultiStream<>(nextStreams),
                "should throw exception when nextStreams of a multiStream contains null");
        assertTrue(exception.getMessage().contains(NEXT_STREAM_NULL), "nextStream should not be null");
    }
}
