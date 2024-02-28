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

import com.swirlds.base.test.fixtures.concurrent.TestExecutor;
import com.swirlds.base.test.fixtures.concurrent.WithTestExecutor;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.api.Logger;
import com.swirlds.logging.api.internal.LoggingSystem;
import com.swirlds.logging.util.InMemoryHandler;
import com.swirlds.logging.util.LoggingTestUtils;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@WithTestExecutor
@Tag(TIMING_SENSITIVE)
public class LoggingSystemStressTest {

    @Test
    void testMultipleLoggersInParallel(TestExecutor testExecutor) {
        // given
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final LoggingSystem loggingSystem = new LoggingSystem(configuration);
        final InMemoryHandler handler = new InMemoryHandler(configuration);
        loggingSystem.addHandler(handler);
        final List<Runnable> runnables = IntStream.range(0, 100)
                .mapToObj(i -> loggingSystem.getLogger("logger-" + i))
                .map(l -> (Runnable) () -> LoggingTestUtils.generateExtensiveLogMessages(l))
                .collect(Collectors.toList());

        // when
        testExecutor.executeAndWait(runnables);

        // then
        Assertions.assertEquals(140000, handler.getEvents().size());
        IntStream.range(0, 100)
                .forEach(i -> Assertions.assertEquals(
                        1400,
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
        final List<Runnable> runnables = IntStream.range(0, 100)
                .mapToObj(l -> (Runnable) () -> LoggingTestUtils.generateExtensiveLogMessages(logger))
                .collect(Collectors.toList());

        // when
        testExecutor.executeAndWait(runnables);

        // then
        Assertions.assertEquals(140000, handler.getEvents().size());
    }
}
