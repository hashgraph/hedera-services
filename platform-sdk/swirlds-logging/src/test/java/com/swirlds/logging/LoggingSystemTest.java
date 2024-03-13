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

import static com.swirlds.logging.util.LoggingTestUtils.EXPECTED_STATEMENTS;
import static com.swirlds.logging.util.LoggingTestUtils.countLinesInStatements;
import static com.swirlds.logging.util.LoggingTestUtils.getLines;
import static com.swirlds.logging.util.LoggingTestUtils.linesToStatements;

import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.file.FileHandler;
import com.swirlds.logging.test.fixtures.internal.LoggingMirrorImpl;
import com.swirlds.logging.util.LoggingTestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@WithTestExecutor
@Disabled
public class LoggingSystemTest {

    private static final String LOG_FILE = "log-files/logging.log";

    @Test
    void testFileHandlerLogging() throws IOException {

        // given
        final String logFile = LoggingTestUtils.prepareLoggingFile(LOG_FILE);
        final String fileHandlerName = "file";
        final Configuration configuration = LoggingTestUtils.prepareConfiguration(logFile, fileHandlerName);
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final FileHandler handler = new FileHandler(fileHandlerName, configuration, true);
        final LoggingMirrorImpl mirror = new LoggingMirrorImpl();
        loggingSystem.addHandler(handler);
        loggingSystem.addHandler(mirror);
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
}
