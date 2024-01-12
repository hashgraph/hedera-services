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

package com.swirlds.logging;

import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.logging.api.internal.level.MarkerState;
import com.swirlds.logging.util.LoggingTestOrchestrator;
import com.swirlds.logging.util.LoggingTestOrchestrator.TestScenario;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class HandlerLoggingLevelConfigConcurrentUpdateTest {

    @Test
    public void testConcurrentConfigUpdate() throws InterruptedException {

        // Creates the configuration
        HandlerLoggingLevelConfig configUnderTest =
                new HandlerLoggingLevelConfig(defaultConfigBuilder().getOrCreateConfig());

        // Ask the Orchestrator to run all desired scenarios up to 2 Seconds
        LoggingTestOrchestrator.runScenarios(
                configUnderTest,
                Duration.of(2, ChronoUnit.SECONDS),
                defaultScenario(),
                scenario1(),
                scenario2(),
                scenario3(),
                scenario4(),
                scenario5());
    }

    private static TestConfigBuilder defaultConfigBuilder() {
        return new TestConfigBuilder()
                .withValue("logging.level", Level.INFO)
                .withConverter(ConfigLevel.class, new ConfigLevelConverter())
                .withConverter(MarkerState.class, new MarkerStateConverter());
    }

    // Default scenario: what should happen if no configuration is reloaded
    private static TestScenario defaultScenario() {
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("default")
                .assertThat("", Level.ERROR, true)
                .assertThat("", Level.WARN, true)
                .assertThat("", Level.INFO, true)
                .assertThat("", Level.DEBUG, false)
                .build();
    }

    // Scenario 1: Change logging level
    private static TestScenario scenario1() {
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("scenario1")
                .withConfigurationFrom(Map.of("logging.level", Level.TRACE))
                .assertThat("com.sample.Class", Level.TRACE, true) // Fix Scenario
                .assertThat("com.Class", Level.INFO, true)
                .assertThat("Class", Level.TRACE, true)
                .build();
    }

    // Scenario 2: No Specific Configuration for Package
    private static TestScenario scenario2() {
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("scenario2")
                .withConfigurationFrom(Map.of("logging.level", Level.WARN))
                .assertThat("com.sample.Class", Level.DEBUG, false)
                .build();
    }

    // Scenario 3: Class-Specific Configuration
    private static TestScenario scenario3() {
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("scenario3")
                .withConfigurationFrom(Map.of("logging.level.com.sample.package.Class", Level.ERROR))
                .assertThat("com.sample.package.Class", Level.ERROR, true)
                .build();
    }

    // Scenario 4: Package-Level Configuration
    private static TestScenario scenario4() {
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("scenario4")
                .withConfigurationFrom(Map.of("logging.level.com.sample.package", Level.TRACE))
                .assertThat("com.sample.package.Class", Level.TRACE, true) // FIX SCENARIO
                .build();
    }

    // Scenario 5: Mixed Configuration
    private static TestScenario scenario5() {
        String subPackage = ".sub";
        String clazz = ".Random";
        String subpackageSubClazz = ".sub.Random";
        return LoggingTestOrchestrator.TestScenario.builder()
                .name("scenario5")
                .withConfigurationFrom(Map.of(
                        "logging.level",
                        Level.WARN,
                        "logging.level.com.sample",
                        Level.INFO,
                        "logging.level.com.sample.package",
                        Level.ERROR))
                .assertThat("Class", Level.ERROR, true)
                .assertThat("Class", Level.WARN, true)
                .assertThat("Class", Level.INFO, false)
                .assertThat("Class", Level.DEBUG, false)
                .assertThat("Class", Level.TRACE, false)
                .assertThat("a" + clazz, Level.WARN, true)
                .assertThat("b" + clazz, Level.INFO, false)
                .assertThat("c" + clazz, Level.DEBUG, false)
                .assertThat("d" + clazz, Level.TRACE, false)
                .assertThat("other" + clazz, Level.ERROR, true)
                .assertThat("other.a" + clazz, Level.WARN, true)
                .assertThat("other.b" + clazz, Level.INFO, false)
                .assertThat("other.c" + clazz, Level.DEBUG, false)
                .assertThat("other.d" + clazz, Level.TRACE, false)
                .assertThat("com.sample" + clazz, Level.ERROR, true)
                .assertThat("com.sample" + clazz, Level.WARN, true)
                .assertThat("com.sample" + clazz, Level.INFO, true)
                .assertThat("com.sample" + clazz, Level.DEBUG, false)
                .assertThat("com.sample" + clazz, Level.TRACE, false)
                .assertThat("com.sample.package" + clazz, Level.ERROR, true)
                .assertThat("com.sample.package" + clazz, Level.WARN, false)
                .assertThat("com.sample.package" + clazz, Level.INFO, false)
                .assertThat("com.sample.package" + clazz, Level.DEBUG, false)
                .assertThat("com.sample.package" + clazz, Level.TRACE, false)
                .assertThat("com.sample" + subPackage, Level.WARN, true)
                .assertThat("com.sample" + subpackageSubClazz, Level.INFO, true)
                .build();
    }
}
