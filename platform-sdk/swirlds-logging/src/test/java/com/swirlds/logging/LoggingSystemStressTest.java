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

import static com.swirlds.common.test.fixtures.junit.tags.TestQualifierTags.TIMING_SENSITIVE;
import static com.swirlds.logging.util.LoggingTestUtils.EXPECTED_STATEMENTS;
import static com.swirlds.logging.util.LoggingTestUtils.countNewLines;
import static com.swirlds.logging.util.LoggingTestUtils.getLines;
import static com.swirlds.logging.util.LoggingTestUtils.linesToStatements;

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.api.internal.level.MarkerState;
import com.swirlds.logging.file.FileHandler;
import com.swirlds.logging.test.fixtures.internal.LoggingMirrorImpl;
import com.swirlds.logging.util.InMemoryHandler;
import com.swirlds.logging.util.LoggingTestUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@WithTestExecutor
@Tag(TIMING_SENSITIVE)
public class LoggingSystemStressTest {

    private static final int TOTAL_RUNNABLE = 100;
    private static final String LOG_FILE = "log-files/logging.log";

    @Test
    void testMultipleLoggersInParallel(TestExecutor testExecutor) {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);
        final List<Runnable> runnables = IntStream.range(0, TOTAL_RUNNABLE)
                .mapToObj(i -> loggingSystem.getLogger("logger-" + i))
                .map(l -> (Runnable) () -> LoggingTestUtils.generateExtensiveLogMessages(l))
                .collect(Collectors.toList());

        // when
        testExecutor.executeAndWait(runnables);

        // then
        Assertions.assertEquals(
                EXPECTED_STATEMENTS * TOTAL_RUNNABLE, handler.getEvents().size());
        IntStream.range(0, TOTAL_RUNNABLE)
                .forEach(i -> Assertions.assertEquals(
                        EXPECTED_STATEMENTS,
                        handler.getEvents().stream()
                                .filter(e -> Objects.equals(e.loggerName(), "logger-" + i))
                                .count()));
    }

    @Test
    void testOneLoggerInParallel(TestExecutor testExecutor) {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();

        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final Logger logger = loggingSystem.getLogger("logger");
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);

        // when
        doLog(testExecutor, logger, TOTAL_RUNNABLE);

        // then
        Assertions.assertEquals(
                EXPECTED_STATEMENTS * TOTAL_RUNNABLE, handler.getEvents().size());
    }

    @Test
    void testFileLoggingFileMultipleEventsInParallel(TestExecutor testExecutor) throws IOException {

        // given
        final String logFile = prepareLoggingFile();
        final String fileHandlerName = "file";
        final Configuration configuration = prepareConfiguration(logFile, fileHandlerName);
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final FileHandler handler = new FileHandler(fileHandlerName, configuration);
        final LoggingMirrorImpl mirror = new LoggingMirrorImpl();
        loggingSystem.addHandler(handler);
        loggingSystem.addHandler(mirror);
        // A random log name, so it's easier to combine lines after
        final String loggerName = UUID.randomUUID().toString();
        final Logger logger = loggingSystem.getLogger(loggerName);

        // when
        doLog(testExecutor, logger, 10);
        loggingSystem.stopAndFinalize();

        try {
            final List<String> statementsInMirror = formatMirrorEvents(mirror);
            final List<String> logLines = getLines(logFile);
            final List<String> statementsInFile = linesToStatements(loggerName, logLines);

            // then
            Assertions.assertEquals(EXPECTED_STATEMENTS * 10, statementsInFile.size());
            final int expectedLineCountInFile = countNewLines(statementsInMirror) + statementsInMirror.size();
            Assertions.assertEquals(expectedLineCountInFile, (long) logLines.size());
            org.assertj.core.api.Assertions.assertThat(statementsInMirror).isSubsetOf(statementsInFile);

        } finally {
            Files.deleteIfExists(Path.of(logFile));
        }
    }

    private static List<String> formatMirrorEvents(final LoggingMirrorImpl mirror) {
        final LineBasedFormat formattedEvents = new LineBasedFormat(false);
        return mirror.getEvents().stream()
                .map(e -> {
                    final StringBuilder stringBuilder = new StringBuilder();
                    formattedEvents.print(stringBuilder, e);
                    stringBuilder.setLength(stringBuilder.length() - 1);
                    return stringBuilder.toString();
                })
                .collect(Collectors.toList());
    }

    private static void doLog(final TestExecutor testExecutor, final Logger logger, final int totalRunnable) {
        testExecutor.executeAndWait(IntStream.range(0, totalRunnable)
                .mapToObj(l -> (Runnable) () -> LoggingTestUtils.generateExtensiveLogMessages(logger))
                .collect(Collectors.toList()));
    }

    private static String prepareLoggingFile() throws IOException {
        final File testMultipleLoggersInParallel = new File(LOG_FILE);
        Files.deleteIfExists(testMultipleLoggersInParallel.toPath());
        return testMultipleLoggersInParallel.getAbsolutePath();
    }

    private static Configuration prepareConfiguration(final String logFile, final String fileHandlerName) {
        return new TestConfigBuilder()
                .withConverter(ConfigLevel.class, new ConfigLevelConverter())
                .withConverter(MarkerState.class, new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.%s.type".formatted(fileHandlerName), "file")
                .withValue("logging.handler.%s.active".formatted(fileHandlerName), "true")
                .withValue("logging.handler.%s.formatTimestamp".formatted(fileHandlerName), "false")
                .withValue("logging.handler.%s.level".formatted(fileHandlerName), "trace")
                .withValue("logging.handler.%s.file".formatted(fileHandlerName), logFile)
                .getOrCreateConfig();
    }
}
