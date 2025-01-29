/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.turtle;

import com.swirlds.common.io.config.FileSystemManagerConfig;
import com.swirlds.common.io.utility.FileUtils;
import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.test.fixtures.turtle.runner.Turtle;
import com.swirlds.platform.test.fixtures.turtle.runner.TurtleBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * This test needs have the following tech-debt:
 * <ul>
 *     <li>We need validation. No point in running a test if you can't validate if it is a pass/fail.</li>
 *     <li>We need to ensure that all resources used by TURTLE are properly freed up after the test is run.</li>
 *     <li>We need a proper way of setting a temporal filesystem.</li>
 *     <li>We need safeguards/detection against test deadlock. These tests are sufficiently complex as to make
 *         deadlocks a real possibility, and so it would be good to make the framework handle deadlocks.</li>
 * </ul>
 */
class TurtleTests {

    @AfterAll
    static void tearDown() throws IOException {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        var value = configuration.getConfigData(FileSystemManagerConfig.class);
        FileUtils.deleteDirectory(Path.of(value.rootPath()));
        Files.deleteIfExists(Path.of("settingsUsed.txt"));
        Files.deleteIfExists(Path.of("swirlds.log"));
    }
    /**
     * Simulate a turtle network for 5 minutes.
     */
    @Test
    void turtleTest() {
        final Randotron randotron = Randotron.create();

        final Turtle turtle = TurtleBuilder.create(randotron)
                .withNodeCount(4)
                .withSimulationGranularity(Duration.ofMillis(10))
                .withTimeReportingEnabled(true)
                .build();

        turtle.start();
        turtle.simulateTime(Duration.ofMinutes(5));
    }
}
