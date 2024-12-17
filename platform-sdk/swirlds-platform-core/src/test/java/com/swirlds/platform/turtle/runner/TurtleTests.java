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

package com.swirlds.platform.turtle.runner;

import com.swirlds.common.test.fixtures.Randotron;
import java.time.Duration;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class TurtleTests {

    /**
     * Simulate a turtle network for 5 minutes.
     * <p>
     * This test needs to remain disabled until the following things are resolved:
     * <ul>
     *     <li>We need validation. No point in running a test if you can't validate if it is a pass/fail.</li>
     *     <li>We need to work out the proper place for these tests in the CI lifecycle. TURTLE tests are long for a
     *         unit test, so we need to be careful where we run them.</li>
     *     <li>We need to ensure that all resources used by TURTLE are properly freed up after the test is run.
     *         Currently turtle leaves a bunch of junk on the file system.</li>
     *     <li>We need safeguards/detection against test deadlock. These tests are sufficiently complex as to make
     *         deadlocks a real possibility, and so it would be good to make the framework handle deadlocks.</li>
     * </ul>
     */
    @Disabled
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
