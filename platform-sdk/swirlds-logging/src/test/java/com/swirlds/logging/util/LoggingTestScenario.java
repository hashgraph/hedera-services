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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

/**
 * {@link LoggingTestScenario} is a relationship between a desired configuration and the list of assertions to verify
 * for that config
 */
public final class LoggingTestScenario {
    private final String scenarioName;
    private final TestConfigBuilder scenarioConfigBuilder;
    private final List<AssertionRule> assertionsRules;

    private LoggingTestScenario(String scenarioName, TestConfigBuilder builder, List<AssertionRule> assertionsRules) {
        this.scenarioName = scenarioName;
        this.scenarioConfigBuilder = builder;
        this.assertionsRules = assertionsRules;
    }

    public Configuration configuration() {
        return this.scenarioConfigBuilder.getOrCreateConfig();
    }

    public static LoggingTestScenarioBuilder builder() {
        return new LoggingTestScenarioBuilder();
    }

    public static class LoggingTestScenarioBuilder {
        private String name;
        private final Map<String, Object> properties;
        private final List<AssertionRule> assertionsRules;

        private LoggingTestScenarioBuilder() {
            this.properties = new HashMap<>();
            this.assertionsRules = new ArrayList<>();
        }

        public LoggingTestScenarioBuilder name(String name) {
            this.name = name;
            return this;
        }

        public LoggingTestScenarioBuilder withConfiguration(String key, Object value) {
            this.properties.put(key, value);
            return this;
        }

        public LoggingTestScenarioBuilder assertThatLevelIsAllowed(String loggerName, Level level) {
            assertionsRules.add(new AssertionRule(loggerName, level, true));
            return this;
        }

        public LoggingTestScenarioBuilder assertThatLevelIsNotAllowed(String propertyName, Level level) {
            assertionsRules.add(new AssertionRule(propertyName, level, false));
            return this;
        }

        public LoggingTestScenario build() {
            TestConfigBuilder testConfigBuilder = new TestConfigBuilder();
            properties.forEach(testConfigBuilder::withValue);
            return new LoggingTestScenario(this.name, testConfigBuilder, assertionsRules);
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
