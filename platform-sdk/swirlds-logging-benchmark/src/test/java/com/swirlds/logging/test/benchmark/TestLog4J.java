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

import com.swirlds.logging.benchmark.ConfigureLog4J;
import com.swirlds.logging.benchmark.LogWithLog4J;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.spi.LoggerContext;
import org.junit.jupiter.api.Test;

public class TestLog4J {

    @Test
    void testFile() {
        final LoggerContext loggerContext = ConfigureLog4J.configureFileLogging();
        Logger logger = loggerContext.getLogger("TestLogging4J");
        LogWithLog4J logRunner = new LogWithLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logRunner.run());
    }

    @Test
    void testConsole() {
        final LoggerContext loggerContext = ConfigureLog4J.configureConsoleLogging();
        Logger logger = loggerContext.getLogger("TestLogging4J");
        LogWithLog4J logRunner = new LogWithLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logRunner.run());
    }

    @Test
    void testFileAndConsole() {
        final LoggerContext loggerContext = ConfigureLog4J.configureFileAndConsoleLogging();
        Logger logger = loggerContext.getLogger("TestLogging4J");
        LogWithLog4J logRunner = new LogWithLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logRunner.run());
    }

    @Test
    void testFileAsync() {
        LoggerContext loggingSystem = ConfigureLog4J.configureFileLogging();
        Logger logger = loggingSystem.getLogger("TestLogging4J");
        final List<LogWithLog4J> list =
                IntStream.range(0, 10).mapToObj(i -> new LogWithLog4J(logger)).toList();

        final ExecutorService executorService = Executors.newFixedThreadPool(10);
        list.forEach(executorService::submit);
        try {
            Thread.sleep(5_000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
