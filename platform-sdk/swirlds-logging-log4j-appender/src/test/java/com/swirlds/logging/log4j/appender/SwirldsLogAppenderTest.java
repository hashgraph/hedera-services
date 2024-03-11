/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.log4j.appender;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.legacy.LogMarker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SwirldsLogAppenderTest {
    private static final String LOGGER_NAME = "testLogger";

    public LoggingSystem setup(final com.swirlds.config.api.Configuration configuration) {
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);

        loggingSystem.installHandlers();
        loggingSystem.installProviders();

        return loggingSystem;
    }

    public static Stream<Arguments> levelsAndSizes() {
        return Stream.of(
                Arguments.of(Level.TRACE, 5),
                Arguments.of(Level.DEBUG, 4),
                Arguments.of(Level.INFO, 3),
                Arguments.of(Level.WARN, 2),
                Arguments.of(Level.ERROR, 1),
                Arguments.of(Level.OFF, 0));
    }

    @ParameterizedTest
    @MethodSource("levelsAndSizes")
    public void loggingLevels(final Level level, final int expectedSize) throws Exception {
        // given
        final Path filePath = Path.of("%s_test.log".formatted(level));
        try {
            final var configuration = new TestConfigBuilder()
                    .withValue("logging.level", level)
                    .withValue("logging.handler.DEFAULT.enabled", true)
                    .withValue("logging.handler.DEFAULT.type", "file")
                    .withValue("logging.handler.DEFAULT.file", filePath)
                    .getOrCreateConfig();

            final LoggingSystem loggingSystem = setup(configuration);
            final Logger testLogger = LogManager.getLogger(LOGGER_NAME);

            // when
            testLogger.error("This is an error message");
            testLogger.warn("This is a warn message");
            testLogger.info("This is an info message");
            testLogger.debug("This is a debug message");
            testLogger.trace("This is a trace message");

            // then
            loggingSystem.stopAndFinalize();
            final List<String> logLines = Files.lines(filePath).toList();

            assertThat(logLines).hasSize(expectedSize);
        } finally {
            Files.delete(filePath);
        }
    }

    @Test
    public void loggingSimpleMarker() throws Exception {
        // given
        final Path filePath = Path.of("marker_test.log");
        try {
            final var configuration = new TestConfigBuilder()
                    .withValue("logging.level", "DEBUG")
                    .withValue("logging.handler.DEFAULT.enabled", true)
                    .withValue("logging.handler.DEFAULT.type", "file")
                    .withValue("logging.handler.DEFAULT.file", filePath)
                    .getOrCreateConfig();

            final LoggingSystem loggingSystem = setup(configuration);
            final Logger testLogger = LogManager.getLogger(LOGGER_NAME);

            // when
            testLogger.warn(LogMarker.CONFIG.getMarker(), "This is a warn message");

            // then
            loggingSystem.stopAndFinalize();
            final List<String> logLines = Files.lines(filePath).toList();

            assertThat(logLines).hasSize(1);
            assertThat(logLines.getFirst()).contains(LogMarker.CONFIG.name());
        } finally {
            Files.delete(filePath);
        }
    }

    @Test
    public void loggingChainedMarkers() throws Exception {
        // given
        final Path filePath = Path.of("marker_test.log");
        try {
            final var configuration = new TestConfigBuilder()
                    .withValue("logging.level", "DEBUG")
                    .withValue("logging.handler.DEFAULT.enabled", true)
                    .withValue("logging.handler.DEFAULT.type", "file")
                    .withValue("logging.handler.DEFAULT.file", filePath)
                    .getOrCreateConfig();

            final LoggingSystem loggingSystem = setup(configuration);
            final Logger testLogger = LogManager.getLogger(LOGGER_NAME);

            // when
            testLogger.warn(TestMarkers.childMarker, "This is a warn message");

            // then
            loggingSystem.stopAndFinalize();
            final List<String> logLines = Files.lines(filePath).toList();

            assertThat(logLines).hasSize(1);
            assertThat(logLines.getFirst()).contains(TestMarkers.CHILD, TestMarkers.PARENT, TestMarkers.GRANT);
        } finally {
            Files.delete(filePath);
        }
    }

    @Test
    public void loggingContext() throws Exception {
        // given
        final Path filePath = Path.of("context_test.log");
        try {
            final var configuration = new TestConfigBuilder()
                    .withValue("logging.level", "DEBUG")
                    .withValue("logging.handler.DEFAULT.enabled", true)
                    .withValue("logging.handler.DEFAULT.type", "file")
                    .withValue("logging.handler.DEFAULT.file", filePath)
                    .getOrCreateConfig();

            final LoggingSystem loggingSystem = setup(configuration);
            final Logger testLogger = LogManager.getLogger(LOGGER_NAME);

            // when
            ThreadContext.put("key1", "value1");
            testLogger.warn("This is a warn message");

            // then
            loggingSystem.stopAndFinalize();
            final List<String> logLines = Files.lines(filePath).toList();

            assertThat(logLines).hasSize(1);
            assertThat(logLines.getFirst()).contains("key1=value1");
        } finally {
            Files.delete(filePath);
            ThreadContext.clearAll();
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        final LoggerContext loggerContext = (LoggerContext) LogManager.getContext(false);
        loggerContext.reconfigure();
    }

    private static class TestMarkers {
        public static final Marker grantMarker;
        public static final Marker parentMarker;
        public static final Marker childMarker;

        public static final String GRANT = "GRANT";

        public static final String PARENT = "PARENT";

        public static final String CHILD = "CHILD";

        static {
            grantMarker = MarkerManager.getMarker(GRANT);
            parentMarker = MarkerManager.getMarker(PARENT).addParents(grantMarker);
            childMarker = MarkerManager.getMarker(CHILD).addParents(parentMarker);
        }
    }
}
