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

package com.swirlds.logging.util;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;

/**
 * This class helps the test to:
 *  <ol>
 * <li>Store all {@code TestScenario}s
 * <li>reload the configuration
 * <li>verify the scenario when the configuration is loaded
 *  <ol>
 */
public final class HandlerLoggingLevelConfigTestOrchestrator {
    private final List<TestScenario> scenarios;

    /**
     * Runs the different {@code testScenarios} in random order up to {@code durationLimit}.
     * All scenarios are run at least once.
     */
    public static void runScenarios(
            final HandlerLoggingLevelConfig configUnderTest, Duration durationLimit, TestScenario... testScenarios) {

        HandlerLoggingLevelConfigTestOrchestrator orchestrator =
                new HandlerLoggingLevelConfigTestOrchestrator(testScenarios);

        final List<Integer> list =
                IntStream.rangeClosed(0, testScenarios.length - 1).boxed().collect(Collectors.toList());
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

    private HandlerLoggingLevelConfigTestOrchestrator(TestScenario... testScenarios) {
        this.scenarios = List.of(testScenarios);
    }

    /**
     * Performs the verification of scenario given by its index on the list
     */
    private void testScenario(int scenario, HandlerLoggingLevelConfig config) {
        // Reload Configuration for desired scenario
        config.update(this.scenarios.get(scenario).configuration());
        // Performs the check
        this.scenarios.get(scenario).verifyAssertionRules(config);
    }

    /**
     * TestScenario is a relationship between a desired configuration and the list of assertions to verify for that
     * config
     */
    public static final class TestScenario {
        private final String scenarioName;
        private final TestConfigBuilder scenarioConfigBuilder;
        private final List<AssertionRule> assertionsRules;

        private TestScenario(String scenarioName, TestConfigBuilder builder, List<AssertionRule> assertionsRules) {
            this.scenarioName = scenarioName;
            this.scenarioConfigBuilder = builder;
            this.assertionsRules = assertionsRules;
        }

        Configuration configuration() {
            return this.scenarioConfigBuilder.getOrCreateConfig();
        }

        public static TestScenarioBuilder builder() {
            return new TestScenarioBuilder();
        }

        public static class TestScenarioBuilder {
            private String name;
            private final Map<String, Object> properties;
            private final List<AssertionRule> assertionsRules;

            private TestScenarioBuilder() {
                this.properties = new HashMap<>();
                this.assertionsRules = new ArrayList<>();
            }

            public TestScenarioBuilder name(String name) {
                this.name = name;
                return this;
            }

            public TestScenarioBuilder withConfigurationFrom(Map<String, Object> properties) {
                this.properties.clear();
                this.properties.putAll(properties);
                return this;
            }

            public TestScenarioBuilder assertThat(String propertyName, Level level, Boolean expectedResult) {
                assertionsRules.add(new AssertionRule(propertyName, level, expectedResult));
                return this;
            }

            public TestScenario build() {
                TestConfigBuilder testConfigBuilder = new TestConfigBuilder();
                properties.forEach(testConfigBuilder::withValue);
                return new TestScenario(this.name, testConfigBuilder, assertionsRules);
            }
        }

        public void verifyAssertionRules(HandlerLoggingLevelConfig config) {
            for (AssertionRule rule : this.assertionsRules) {
                rule.performAssert(this.scenarioName, config);
            }
        }

        private record AssertionRule(String propertyName, Level level, Boolean expectedResult) {
            public void performAssert(final String scenarioName, final HandlerLoggingLevelConfig config) {
                if (this.expectedResult() != null) {
                    Assertions.assertEquals(
                            this.expectedResult(),
                            config.isEnabled(this.propertyName(), this.level(), null),
                            String.format(
                                    "Scenario %s: AssertionRule %s: but actual was:%s%n",
                                    scenarioName, this, !this.expectedResult()));
                }
            }
        }
    }
}
