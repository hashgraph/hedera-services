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

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggerImpl;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.event.SimpleLogEventFactory;
import com.swirlds.logging.util.DummyConsumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
public class LoggerImplTest {

    @Test
    void testSimpleLogger() {
        // given
        LoggerImpl logger = new LoggerImpl("test-name", new SimpleLogEventFactory(), new DummyConsumer());

        // when
        final String name = logger.getName();
        final boolean traceEnabled = logger.isTraceEnabled();
        final boolean debugEnabled = logger.isDebugEnabled();
        final boolean infoEnabled = logger.isInfoEnabled();
        final boolean warnEnabled = logger.isWarnEnabled();
        final boolean errorEnabled = logger.isErrorEnabled();

        // then
        Assertions.assertEquals("test-name", name);
        Assertions.assertTrue(traceEnabled);
        Assertions.assertTrue(debugEnabled);
        Assertions.assertTrue(infoEnabled);
        Assertions.assertTrue(warnEnabled);
        Assertions.assertTrue(errorEnabled);
    }

    @Test
    void testNullLogEventConsumer() {
        Assertions.assertThrows(
                NullPointerException.class, () -> new LoggerImpl("test-name", new SimpleLogEventFactory(), null));
    }

    @Test
    void testNullLogEventFactory() {
        Assertions.assertThrows(
                NullPointerException.class, () -> new LoggerImpl("test-name", null, new DummyConsumer()));
    }

    @Test
    void testSpecWithNullName() {
        // given + then
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new LoggerImpl(null, new SimpleLogEventFactory(), new DummyConsumer()));
    }

    @Test
    void testSpecWithSimpleLogger() {
        // given
        LoggerImpl logger = new LoggerImpl("test-name", new SimpleLogEventFactory(), new DummyConsumer());

        // then
        LoggerApiSpecTest.testSpec(logger);
    }

    @Test
    void testSpecWithFileLogHandler() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.file.type", "file")
                .withValue("logging.handler.file.enabled", "true")
                .withValue("logging.handler.file.level", "trace")
                .withValue("logging.handler.file.file", "benchmark.log")
                .build();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.installHandlers();
        final Logger logger = loggingSystem.getLogger("test-name");

        // then
        LoggerApiSpecTest.testSpec(logger);
    }

    @Test
    void testSpecWithDifferentLoggers() {
        LoggerApiSpecTest.testSpec(new LoggerImpl("test-name", new SimpleLogEventFactory(), new DummyConsumer()));
        LoggerApiSpecTest.testSpec(new LoggerImpl("null", new SimpleLogEventFactory(), new DummyConsumer()));
    }
}
