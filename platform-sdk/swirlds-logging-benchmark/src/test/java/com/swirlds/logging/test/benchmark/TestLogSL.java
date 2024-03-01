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

package com.swirlds.logging.test.benchmark;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.benchmark.ConfigureSwirldsLog;
import com.swirlds.logging.benchmark.LogWithSwirlds;
import com.swirlds.logging.test.fixtures.WithLoggingMirror;
import com.swirlds.logging.test.fixtures.internal.LoggingMirrorImpl;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

@WithLoggingMirror
public class TestLogSL {

    public static final String TEST_LOGGING_SL = "TestLoggingSL";

    @Test
    void testFile() {
        LoggingSystem loggingSystem = ConfigureSwirldsLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogWithSwirlds logWithSwirlds = new LogWithSwirlds(logger);

        IntStream.range(0, 10_000).forEach(i -> logWithSwirlds.run());
    }

    @Test
    void testConsole() {
        LoggingSystem loggingSystem = ConfigureSwirldsLog.configureConsoleLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogWithSwirlds logWithSwirlds = new LogWithSwirlds(logger);

        IntStream.range(0, 10_000).forEach(i -> logWithSwirlds.run());
    }

    @Test
    void testFileAndConsole() {
        LoggingSystem loggingSystem = ConfigureSwirldsLog.configureFileAndConsoleLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        LogWithSwirlds logWithSwirlds = new LogWithSwirlds(logger);

        IntStream.range(0, 10_000).forEach(i -> logWithSwirlds.run());
    }

    @Test
    void testFileAsync() {
        LoggingSystem loggingSystem = ConfigureSwirldsLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger(TEST_LOGGING_SL);
        final LoggingMirrorImpl mirror = new LoggingMirrorImpl();
        loggingSystem.addHandler(mirror);

        final List<LogWithSwirlds> list =
                IntStream.range(0, 10).mapToObj(i -> new LogWithSwirlds(logger)).toList();

        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        list.forEach(executorService::submit);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        loggingSystem.stopAndFinalize();
    }
}
