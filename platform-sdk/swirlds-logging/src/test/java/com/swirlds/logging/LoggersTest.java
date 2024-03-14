/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.Loggers;
import com.swirlds.logging.api.extensions.event.LogEvent;
import com.swirlds.logging.test.fixtures.LoggingMirror;
import com.swirlds.logging.test.fixtures.WithLoggingMirror;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@WithLoggingMirror
public class LoggersTest {

    @Inject
    LoggingMirror loggingMirror;

    @Test
    void testLoggerCreationByName() {
        // given
        final String loggerName = "testLoggerCreationByName";

        // when
        final Logger logger = Loggers.getLogger(loggerName);

        // then
        Assertions.assertEquals(loggerName, logger.getName());
    }

    @Test
    @Disabled
    void testLoggerCreationByNullName() {
        // given
        final String loggerName = null;

        // when
        final Logger logger = Loggers.getLogger(loggerName);

        // then
        Assertions.assertEquals("", logger.getName());
    }

    @Test
    void testLoggerCreationByClass() {
        // given
        final Class clazz = LoggersTest.class;

        // when
        final Logger logger = Loggers.getLogger(clazz);

        // then
        Assertions.assertEquals(clazz.getName(), logger.getName());
    }

    @Test
    @Disabled
    void testLoggerCreationByNullClass() {
        // given
        final Class clazz = null;

        // when
        final Logger logger = Loggers.getLogger(clazz);

        // then
        Assertions.assertEquals("", logger.getName());
    }

    @Test
    void testLoggerCreationByClassReturnsUseableLogger() {
        // given
        final Class clazz = LoggersTest.class;
        final Logger logger = Loggers.getLogger(clazz);

        // when
        logger.error("test");

        // then
        Assertions.assertEquals(1, loggingMirror.getEventCount());
        final LogEvent event = loggingMirror.getEvents().get(0);
        Assertions.assertEquals(clazz.getName(), event.loggerName());
        Assertions.assertEquals(Thread.currentThread().getName(), event.threadName());
        Assertions.assertEquals(Level.ERROR, event.level());
    }

    @Test
    void testLoggerCreationByNameReturnsUseableLogger() {
        // given
        final String loggerName = "testLoggerCreationByName";
        final Logger logger = Loggers.getLogger(loggerName);

        // when
        logger.error("test");

        // then
        Assertions.assertEquals(1, loggingMirror.getEventCount());
        final LogEvent event = loggingMirror.getEvents().get(0);
        Assertions.assertEquals(loggerName, event.loggerName());
        Assertions.assertEquals(Thread.currentThread().getName(), event.threadName());
        Assertions.assertEquals(Level.ERROR, event.level());
    }
}
