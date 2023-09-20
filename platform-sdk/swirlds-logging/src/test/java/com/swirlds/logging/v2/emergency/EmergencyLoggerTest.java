/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.v2.emergency;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.io.SystemErrProvider;
import com.swirlds.base.test.fixtures.io.WithSystemError;
import com.swirlds.base.test.fixtures.io.WithSystemOut;
import com.swirlds.logging.v2.Level;
import com.swirlds.logging.v2.Marker;
import com.swirlds.logging.v2.extensions.event.LogEvent;
import com.swirlds.logging.v2.extensions.event.LogMessage;
import com.swirlds.logging.v2.internal.emergency.EmergencyLoggerImpl;
import jakarta.inject.Inject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@WithSystemError
@WithSystemOut
public class EmergencyLoggerTest {

    @Inject
    private SystemErrProvider systemErrProvider;

    @Test
    void testLog1Line() {
        // given
        final EmergencyLoggerImpl emergencyLogger = EmergencyLoggerImpl.getInstance();
        final String threadName = Thread.currentThread().getName();

        // when
        emergencyLogger.log(Level.INFO, "test");

        // then
        assertEquals(1, systemErrProvider.getLines().count());
        assertTrue(systemErrProvider
                .getLines()
                .toList()
                .get(0)
                .endsWith(" INFO [" + threadName + "] EMERGENCY-LOGGER - test"));
    }

    @Test
    void testLogMultipleLines() {
        // given
        final EmergencyLoggerImpl emergencyLogger = EmergencyLoggerImpl.getInstance();

        // when
        emergencyLogger.log(Level.INFO, "test");
        emergencyLogger.log(Level.INFO, "test1");
        emergencyLogger.log(Level.INFO, "test2");
        emergencyLogger.log(Level.INFO, "test3");

        // then
        assertEquals(4, systemErrProvider.getLines().count());
    }

    @Test
    void testDefaultLevel() {
        // given
        final EmergencyLoggerImpl emergencyLogger = EmergencyLoggerImpl.getInstance();

        // when
        emergencyLogger.log(Level.ERROR, "test");
        emergencyLogger.log(Level.WARN, "test");
        emergencyLogger.log(Level.INFO, "test");
        emergencyLogger.log(Level.DEBUG, "test");
        emergencyLogger.log(Level.TRACE, "test");

        // then
        assertEquals(
                4,
                systemErrProvider.getLines().count(),
                "Only ERROR, WARNING, INFO and DEBUG should be logged by default");
    }

    @Test
    void loggerMustBe100Solid() {
        // given
        final EmergencyLoggerImpl emergencyLogger = EmergencyLoggerImpl.getInstance();
        emergencyLogger.publishLoggedEvents(); // clear the queue

        // when
        assertDoesNotThrow(() -> emergencyLogger.log(Level.INFO, null));
        assertDoesNotThrow(() -> emergencyLogger.log(null, "message"));
        assertDoesNotThrow(() -> emergencyLogger.log(null, null));

        assertDoesNotThrow(() -> emergencyLogger.logNPE(null));

        assertDoesNotThrow(() -> emergencyLogger.log(null, "message", new RuntimeException()));
        assertDoesNotThrow(() -> emergencyLogger.log(Level.INFO, null, new RuntimeException()));
        assertDoesNotThrow(() -> emergencyLogger.log(Level.INFO, "message", null));
        assertDoesNotThrow(() -> emergencyLogger.log(null, null, null));

        assertDoesNotThrow(() -> emergencyLogger.log(null));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                Instant.now(),
                (String) null,
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                Instant.now(),
                (LogMessage) null,
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                null,
                "message",
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                null,
                Instant.now(),
                "message",
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                null,
                "threadName",
                Instant.now(),
                "message",
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                null,
                "loggerName",
                "threadName",
                Instant.now(),
                "message",
                new RuntimeException(),
                new Marker("marker"),
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                Instant.now(),
                "message",
                new RuntimeException(),
                null,
                Map.of())));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                Instant.now(),
                "message",
                new RuntimeException(),
                new Marker("marker"),
                null)));
        assertDoesNotThrow(() -> emergencyLogger.log(new LogEvent(
                Level.INFO,
                "loggerName",
                "threadName",
                Instant.now(),
                "message",
                null,
                new Marker("marker"),
                Map.of())));

        // then
        final List<String> allLines = systemErrProvider.getLines().toList();
        final List<String> onlyBasicLines = systemErrProvider
                .getLines()
                .filter(line -> !line.startsWith("\tat "))
                .filter(line -> !line.startsWith("java.lang"))
                .toList();
        final List<String> onlyTrace = systemErrProvider
                .getLines()
                .filter(line -> line.startsWith("\tat "))
                .toList();
        final List<String> onlyException = systemErrProvider
                .getLines()
                .filter(line -> line.startsWith("java.lang"))
                .toList();

        assertEquals(allLines.size(), onlyBasicLines.size() + onlyTrace.size() + onlyException.size());
        assertEquals(19, onlyBasicLines.size());
        assertEquals(13, onlyException.size());
        assertTrue(onlyException.get(0).startsWith(NullPointerException.class.getName()));
        assertTrue(onlyException.get(1).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(2).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(3).startsWith(NullPointerException.class.getName()));
        assertTrue(onlyException.get(4).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(5).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(6).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(7).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(8).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(9).startsWith(NullPointerException.class.getName()));
        assertTrue(onlyException.get(10).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(11).startsWith(RuntimeException.class.getName()));
        assertTrue(onlyException.get(12).startsWith(RuntimeException.class.getName()));

        assertTrue(onlyTrace.size() > 39);

        final List<LogEvent> loggedEvents = emergencyLogger.publishLoggedEvents();
        assertEquals(19, loggedEvents.size());
    }
}
