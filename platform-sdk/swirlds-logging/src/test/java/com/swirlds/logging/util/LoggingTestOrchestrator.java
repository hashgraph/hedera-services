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
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Assertions;

/**
 * This class helps the test to:
 *  <ol>
 * <li>Store all {@code TestScenario}s
 * <li>reload the configuration
 * <li>keep track of which scenario is currently loaded
 * <li>verify the scenario when the configuration is loaded
 *  <ol>
 */
public final class LoggingTestOrchestrator {

    // To be able to stop assertions for previously loaded scenario if the config was reloaded
    private final Lock lock = new ReentrantLock();
    private final AtomicInteger configIndexReference;
    private final List<TestScenario> reloadingConfigAndAssertionRules;

    /**
     * Run the different {@code testScenarios} up to {@code duration} and randomly reloading and updating
     * {@code configUnderTest}.
     * first element on {@code testScenarios} is assumed to be the default configuration
     */
    public static void runScenarios(
            final HandlerLoggingLevelConfig configUnderTest, Duration duration, TestScenario... testScenarios)
            throws InterruptedException {

        LoggingTestOrchestrator orchestrator = new LoggingTestOrchestrator(testScenarios);
        try (ScheduledExecutorService executorService = Executors.newScheduledThreadPool(2)) {
            // Config the test to run up to duration
            final AtomicBoolean continueTest = new AtomicBoolean(true);
            ScheduledFuture<?> continueFuture =
                    executorService.schedule(() -> continueTest.set(false), duration.toMillis(), TimeUnit.MILLISECONDS);
            // concurrently & randomly selects and load a scenario every 10 ms
            ScheduledFuture<?> configUpdateFuture = executorService.scheduleAtFixedRate(
                    () -> orchestrator.randomlySetScenario(configUnderTest), 10, 10, TimeUnit.MILLISECONDS);
            try {
                while (continueTest.get()) { // Keep testing until we run out of time
                    orchestrator.verifyLoadedScenario(configUnderTest);
                }
            } finally {
                continueFuture.cancel(true);
                configUpdateFuture.cancel(true);
            }
            // Shutdown executor and await termination
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        }
    }

    private LoggingTestOrchestrator(TestScenario... testScenarios) {
        this.configIndexReference = new AtomicInteger(0);
        this.reloadingConfigAndAssertionRules = List.of(testScenarios);
    }

    /**
     * Randomly selects a scenario
     */
    public void randomlySetScenario(HandlerLoggingLevelConfig config) {
        // Leaves out the first default scenario given that is only for the initial setting
        int chosenIndex = ThreadLocalRandom.current().nextInt(reloadingConfigAndAssertionRules.size() - 1) + 1;
        lock.lock();
        try {
            // Reload Configuration
            config.update(reloadingConfigAndAssertionRules.get(chosenIndex).configuration());
            this.configIndexReference.set(chosenIndex);
        } finally {
            lock.unlock();
        }
    }

    /**
     * verifies the assertions for selected scenario
     */
    public void verifyLoadedScenario(final HandlerLoggingLevelConfig config) {
        lock.lock(); // We don't want the configuration to change while we are asserting
        final int currentConfigIndex = this.configIndexReference.get();
        try {
            this.reloadingConfigAndAssertionRules.get(currentConfigIndex).verifyAssertionRules(config);
        } finally {
            lock.unlock();
        }
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
