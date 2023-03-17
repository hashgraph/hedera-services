/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.protocol.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.swirlds.common.test.fixtures.FakeTime;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeMessage;
import com.swirlds.platform.chatter.protocol.processing.ProcessingTimeSendReceive;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class ProcessingTimeTests {
    private final Duration procTimeInterval = Duration.ofSeconds(1);
    private final FakeTime time = new FakeTime();

    @Test
    void testHandle() {
        final ProcessingTimeSendReceive procTimeSendReceive =
                new ProcessingTimeSendReceive(time, procTimeInterval, () -> 0);
        assertNull(procTimeSendReceive.getPeerProcessingTime(), "no processing time for this peer should be available");

        final long processingTime = 1000;
        procTimeSendReceive.handleMessage(new ProcessingTimeMessage(processingTime));

        assertNotNull(procTimeSendReceive.getPeerProcessingTime(), "processing time for this peer should be available");
        assertEquals(
                processingTime, procTimeSendReceive.getPeerProcessingTime(), "unexpected processing time for peer");
    }

    @Test
    void testGetMessage() {
        final AtomicLong selfProcessingTime = new AtomicLong(500);
        final ProcessingTimeSendReceive procTimeSendReceive =
                new ProcessingTimeSendReceive(time, procTimeInterval, selfProcessingTime::get);

        ProcessingTimeMessage message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNull(message, "no message should be sent if the required time has not elapsed");

        time.tick(Duration.ofSeconds(2));

        message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNotNull(message, "message should be sent when the interval has elapsed");

        time.tick(Duration.ofMillis(500));
        message = (ProcessingTimeMessage) procTimeSendReceive.getMessage();
        assertNull(message, "message should NOT be sent when the interval since the last sent message has not elapsed");
    }
}
