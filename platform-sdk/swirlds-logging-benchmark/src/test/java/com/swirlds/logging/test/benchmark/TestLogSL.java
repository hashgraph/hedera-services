/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.logging.test.benchmark;

import static com.swirlds.logging.benchmark.LogFileUtlis.getLogStatementsFromLogFile;
import static com.swirlds.logging.benchmark.LogFileUtlis.linesToStatements;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.api.internal.format.LineBasedFormat;
import com.swirlds.logging.benchmark.ConfigureLog;
import com.swirlds.logging.benchmark.LogLikeHell;
import com.swirlds.logging.benchmark.LoggingHandlingType;
import com.swirlds.logging.benchmark.LoggingImplementation;
import com.swirlds.logging.test.fixtures.WithLoggingMirror;
import com.swirlds.logging.test.fixtures.internal.LoggingMirrorImpl;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@WithLoggingMirror
public class TestLogSL {

    public static final String TEST_LOGGING_SL = "TestLoggingSL";

    @Test
    void testFile() {
        LoggingSystem loggingSystem = ConfigureLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testConsole() {
        LoggingSystem loggingSystem = ConfigureLog.configureConsoleLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testFileAndConsole() {
        LoggingSystem loggingSystem = ConfigureLog.configureFileAndConsoleLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testFileAsync() throws IOException {
        LoggingSystem loggingSystem = ConfigureLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        final LoggingMirrorImpl mirror = new LoggingMirrorImpl();
        loggingSystem.addHandler(mirror);

        final int TOTAL = 10;
        final List<LogLikeHell> list = IntStream.range(0, TOTAL)
                .mapToObj(i -> new LogLikeHell(logger))
                .toList();

        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        list.forEach(executorService::submit);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        loggingSystem.stopAndFinalize();

        final List<String> logStatementsFromLogFile = getLogStatementsFromLogFile(
                LoggingImplementation.SWIRLDS,
                LoggingHandlingType.FILE);

        Assertions.assertEquals(75 * TOTAL, (long) logStatementsFromLogFile.size());
        final List<String> statementsInFile = linesToStatements(TEST_LOGGING_SL, logStatementsFromLogFile);
        final LineBasedFormat formattedEvents = new LineBasedFormat(false);
        final List<String> collect = mirror.getEvents().stream()
                .map(e -> {
                    final StringBuilder stringBuilder = new StringBuilder();
                    formattedEvents.print(stringBuilder, e);
                    stringBuilder.setLength(stringBuilder.length() - 1);
                    return stringBuilder.toString();
                })
                .collect(Collectors.toList());

        Assertions.assertEquals(statementsInFile.size(), (long) mirror.getEventCount());
        org.assertj.core.api.
                Assertions.assertThat(collect).isSubsetOf(statementsInFile);
    }


}
