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

package com.swirlds.logging.benchmark;

import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

public class TestLogSL {

    @Test
    void testFile() {
        LoggingSystem loggingSystem = ConfigureLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger("TestLoggingSL");
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testConsole() {
        LoggingSystem loggingSystem = ConfigureLog.configureConsoleLogging();
        Logger logger = loggingSystem.getLogger("TestLoggingSL");
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testFileAndConsole() {
        LoggingSystem loggingSystem = ConfigureLog.configureFileAndConsoleLogging();
        Logger logger = loggingSystem.getLogger("TestLoggingSL");
        LogLikeHell logLikeHell = new LogLikeHell(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testFileAsync() {
        LoggingSystem loggingSystem = ConfigureLog.configureFileLogging();
        Logger logger = loggingSystem.getLogger("TestLoggingSL");

        final List<LogLikeHell> list = IntStream.range(0, 1000)
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
    }
}
