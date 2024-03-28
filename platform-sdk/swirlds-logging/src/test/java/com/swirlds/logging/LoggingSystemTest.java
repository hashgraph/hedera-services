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

import static com.swirlds.base.test.fixtures.assertions.AssertionUtils.assertThrowsNPE;
import static com.swirlds.logging.util.LoggingTestUtils.EXPECTED_STATEMENTS;
import static com.swirlds.logging.util.LoggingTestUtils.countLinesInStatements;
import static com.swirlds.logging.util.LoggingTestUtils.getLines;
import static com.swirlds.logging.util.LoggingTestUtils.linesToStatements;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.extensions.handler.LogHandler;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.test.fixtures.InMemoryHandler;
import com.swirlds.logging.test.fixtures.internal.LoggingMirrorImpl;
import com.swirlds.logging.util.LoggingSystemTestOrchestrator;
import com.swirlds.logging.util.LoggingTestScenario;
import com.swirlds.logging.util.LoggingTestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LoggingSystemTest {

    private static final String LOG_FILE = "log-files/logging.log";
    private static final Duration MAX_DURATION = Duration.of(2, ChronoUnit.SECONDS);

    @Test
    void testFileHandlerLogging() throws IOException {

        // given
        final String logFile = LoggingTestUtils.prepareLoggingFile(LOG_FILE);
        final String fileHandlerName = "file";
        final Configuration configuration = LoggingTestUtils.prepareConfiguration(logFile, fileHandlerName);
        final LoggingMirrorImpl mirror = new LoggingMirrorImpl();
        final LoggingSystem loggingSystem = loggingSystem(configuration, mirror);
        // A random log name, so it's easier to combine lines after
        final String loggerName = UUID.randomUUID().toString();
        final Logger logger = loggingSystem.getLogger(loggerName);

        // when
        LoggingTestUtils.loggExtensively(logger);
        loggingSystem.stopAndFinalize();

        try {
            final List<String> statementsInMirror = LoggingTestUtils.mirrorToStatements(mirror);
            final List<String> logLines = getLines(logFile);
            final List<String> statementsInFile = linesToStatements(logLines);

            // then
            org.assertj.core.api.Assertions.assertThat(statementsInFile.size()).isEqualTo(EXPECTED_STATEMENTS);

            // Loglines should be 1 per statement in mirror + 1 for each stament
            final int expectedLineCountInFile = countLinesInStatements(statementsInMirror);
            org.assertj.core.api.Assertions.assertThat((long) logLines.size()).isEqualTo(expectedLineCountInFile);
            org.assertj.core.api.Assertions.assertThat(statementsInFile).isSubsetOf(statementsInMirror);

        } finally {
            Files.deleteIfExists(Path.of(logFile));
        }
    }

    @Test
    @DisplayName("Multiple configuration updates test")
    public void testConfigUpdate() {

        final Configuration configuration = LoggingTestUtils.getConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = loggingSystem(configuration);

        // Ask the Orchestrator to run all desired scenarios up to 2 Seconds
        // Scenarios define a configuration and a set of assertions for that config.
        LoggingSystemTestOrchestrator.runScenarios(
                loggingSystem,
                MAX_DURATION,
                defaultScenario(),
                scenario1(),
                scenario2(),
                scenario3(),
                scenario4(),
                scenario5());
    }

    @Test
    void testSimpleConfigUpdate() {
        // given
        final Configuration initialConfiguration = LoggingTestUtils.getConfigBuilder()
                .withValue("logging.level", Level.ERROR)
                .getOrCreateConfig();
        final Configuration updatedConfiguration = LoggingTestUtils.getConfigBuilder()
                .withValue("logging.level", Level.TRACE)
                .getOrCreateConfig();
        final LoggingSystem system = loggingSystem(initialConfiguration);

        // when
        system.update(updatedConfiguration);

        // then
        assertTrue(system.isEnabled("com.sample.Foo", Level.TRACE, null));
    }

    @Test
    void testConfigUpdateWithNullConfig() {
        // given
        final Configuration initialConfiguration =
                LoggingTestUtils.getConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(initialConfiguration);
        final Configuration updatedConfiguration = null;

        // then
        assertThrowsNPE(() -> loggingSystem.update(updatedConfiguration));
    }

    @Test
    void testWithConfigUpdateWitEmptyConfig() {
        // given
        final Configuration configuration = LoggingTestUtils.getConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "ERROR")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final LoggingSystem loggingSystem = loggingSystem(configuration);

        // when
        final LoggingTestScenario scenario = LoggingTestScenario.builder()
                .name("reloaded")
                // empty configuration
                .assertThatLevelIsAllowed("", Level.INFO)
                .assertThatLevelIsAllowed("a.long.name.for.a.logger", Level.INFO)
                .assertThatLevelIsAllowed("com.sample", Level.INFO)
                .assertThatLevelIsAllowed("com.sample.Class", Level.INFO)
                .assertThatLevelIsAllowed("com.sample.package", Level.INFO)
                .assertThatLevelIsAllowed("com.sample.package.ClassA", Level.INFO)
                .assertThatLevelIsAllowed("com.sample.package.ClassB", Level.INFO)
                .assertThatLevelIsAllowed("com.sample.package.ClassC", Level.INFO)
                .build();

        // then
        LoggingSystemTestOrchestrator.runScenarios(loggingSystem, scenario);
    }

    private static LoggingSystem loggingSystem(final Configuration configuration) {
        return loggingSystem(configuration, new InMemoryHandler(configuration));
    }

    private static LoggingSystem loggingSystem(final Configuration configuration, LogHandler... handlers) {
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        loggingSystem.installHandlers();
        for (LogHandler handler : handlers) {
            loggingSystem.addHandler(handler);
        }
        return loggingSystem;
    }

    // Default scenario: what should happen if no configuration is reloaded
    private static LoggingTestScenario defaultScenario() {
        return LoggingTestScenario.builder()
                .name("default")
                .assertThatLevelIsAllowed("", Level.ERROR)
                .assertThatLevelIsAllowed("", Level.WARN)
                .assertThatLevelIsAllowed("", Level.INFO)
                .assertThatLevelIsNotAllowed("", Level.DEBUG)
                .build();
    }

    // Scenario 1: Change logging level
    private static LoggingTestScenario scenario1() {
        return LoggingTestScenario.builder()
                .name("scenario1")
                .withConfiguration("logging.level", Level.TRACE)
                .assertThatLevelIsAllowed("com.sample.Class", Level.TRACE) // Fix Scenario
                .assertThatLevelIsAllowed("com.Class", Level.INFO)
                .assertThatLevelIsAllowed("Class", Level.TRACE)
                .build();
    }

    // Scenario 2: No Specific Configuration for Package
    private static LoggingTestScenario scenario2() {
        return LoggingTestScenario.builder()
                .name("scenario2")
                .withConfiguration("logging.level", Level.WARN)
                .assertThatLevelIsNotAllowed("com.sample.Class", Level.DEBUG)
                .build();
    }

    // Scenario 3: Class-Specific Configuration
    private static LoggingTestScenario scenario3() {
        return LoggingTestScenario.builder()
                .name("scenario3")
                .withConfiguration("logging.level.com.sample.package.Class", Level.ERROR)
                .assertThatLevelIsAllowed("com.sample.package.Class", Level.ERROR)
                .build();
    }

    // Scenario 4: Package-Level Configuration
    private static LoggingTestScenario scenario4() {
        return LoggingTestScenario.builder()
                .name("scenario4")
                .withConfiguration("logging.level.com.sample.package", Level.TRACE)
                .assertThatLevelIsAllowed("com.sample.package.Class", Level.TRACE) // FIX SCENARIO
                .build();
    }

    // Scenario 5: Mixed Configuration
    private static LoggingTestScenario scenario5() {
        String subPackage = ".sub";
        String clazz = ".Random";
        String subpackageSubClazz = ".sub.Random";
        return LoggingTestScenario.builder()
                .name("scenario5")
                .withConfiguration("logging.level", Level.WARN)
                .withConfiguration("logging.level.com.sample", Level.INFO)
                .withConfiguration("logging.level.com.sample.package", Level.ERROR)
                .assertThatLevelIsAllowed("Class", Level.ERROR)
                .assertThatLevelIsAllowed("Class", Level.WARN)
                .assertThatLevelIsNotAllowed("Class", Level.INFO)
                .assertThatLevelIsNotAllowed("Class", Level.DEBUG)
                .assertThatLevelIsNotAllowed("Class", Level.TRACE)
                .assertThatLevelIsAllowed("a" + clazz, Level.WARN)
                .assertThatLevelIsNotAllowed("b" + clazz, Level.INFO)
                .assertThatLevelIsNotAllowed("c" + clazz, Level.DEBUG)
                .assertThatLevelIsNotAllowed("d" + clazz, Level.TRACE)
                .assertThatLevelIsAllowed("other" + clazz, Level.ERROR)
                .assertThatLevelIsAllowed("other.a" + clazz, Level.WARN)
                .assertThatLevelIsNotAllowed("other.b" + clazz, Level.INFO)
                .assertThatLevelIsNotAllowed("other.c" + clazz, Level.DEBUG)
                .assertThatLevelIsNotAllowed("other.d" + clazz, Level.TRACE)
                .assertThatLevelIsAllowed("com.sample" + clazz, Level.ERROR)
                .assertThatLevelIsAllowed("com.sample" + clazz, Level.WARN)
                .assertThatLevelIsAllowed("com.sample" + clazz, Level.INFO)
                .assertThatLevelIsNotAllowed("com.sample" + clazz, Level.DEBUG)
                .assertThatLevelIsNotAllowed("com.sample" + clazz, Level.TRACE)
                .assertThatLevelIsAllowed("com.sample.package" + clazz, Level.ERROR)
                .assertThatLevelIsNotAllowed("com.sample.package" + clazz, Level.WARN)
                .assertThatLevelIsNotAllowed("com.sample.package" + clazz, Level.INFO)
                .assertThatLevelIsNotAllowed("com.sample.package" + clazz, Level.DEBUG)
                .assertThatLevelIsNotAllowed("com.sample.package" + clazz, Level.TRACE)
                .assertThatLevelIsAllowed("com.sample" + subPackage, Level.WARN)
                .assertThatLevelIsAllowed("com.sample" + subpackageSubClazz, Level.INFO)
                .build();
    }
}
