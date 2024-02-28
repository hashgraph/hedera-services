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

import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;

public class TestLog4J {

    @Test
    void testFile() {
        ConfigureLog4J.configureFileLogging();
        Logger logger = LogManager.getLogger("TestLogging4J");
        LogLikeHellLog4J logLikeHell = new LogLikeHellLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testConsole() {
        ConfigureLog4J.configureConsoleLogging();
        Logger logger = LogManager.getLogger("TestLogging4J");
        LogLikeHellLog4J logLikeHell = new LogLikeHellLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }

    @Test
    void testFileAndConsole() {
        ConfigureLog4J.configureFileAndConsoleLogging();
        Logger logger = LogManager.getLogger("TestLogging4J");
        LogLikeHellLog4J logLikeHell = new LogLikeHellLog4J(logger);

        IntStream.range(0, 10_000).forEach(i -> logLikeHell.run());
    }
}
