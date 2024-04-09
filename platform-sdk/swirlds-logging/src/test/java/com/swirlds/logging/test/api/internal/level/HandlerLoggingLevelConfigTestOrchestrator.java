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

package com.swirlds.logging.test.api.internal.level;

import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.logging.util.LoggingTestScenario;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class helps the test to:
 *  <ol>
 * <li>Store all {@code LoggingTestScenario}
 * <li>reload the configuration
 * <li>verify the scenario when the configuration is loaded
 *  <ol>
 */
public final class HandlerLoggingLevelConfigTestOrchestrator {
    private final List<LoggingTestScenario> scenarios;

    /**
     * Runs the different {@code testScenarios} in random order up to {@code durationLimit}.
     * All scenarios are run at least once.
     */
    public static void runScenarios(
            final HandlerLoggingLevelConfig configUnderTest,
            Duration durationLimit,
            LoggingTestScenario... loggingTestScenarios) {

        HandlerLoggingLevelConfigTestOrchestrator orchestrator =
                new HandlerLoggingLevelConfigTestOrchestrator(loggingTestScenarios);

        final List<Integer> list = IntStream.rangeClosed(0, loggingTestScenarios.length - 1)
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(list);
        long startTime = System.currentTimeMillis();

        for (Integer index : list) {
            orchestrator.testScenario(index, configUnderTest);
        }

        long availableTime = durationLimit.toMillis() - (System.currentTimeMillis() - startTime);
        for (int i = 0; availableTime > 0; i++) {
            orchestrator.testScenario(list.get(i % list.size()), configUnderTest);
            availableTime -= (System.currentTimeMillis() - startTime);
        }
    }

    private HandlerLoggingLevelConfigTestOrchestrator(LoggingTestScenario... loggingTestScenarios) {
        this.scenarios = List.of(loggingTestScenarios);
    }

    /**
     * Performs the verification of scenario given by its index on the list
     */
    private void testScenario(int scenario, HandlerLoggingLevelConfig config) {
        System.out.printf("Testing scenario %d%n", scenario);
        // Reload Configuration for desired scenario
        config.update(this.scenarios.get(scenario).configuration());
        // Performs the check
        this.scenarios.get(scenario).verifyAssertionRules(config);
    }
}
