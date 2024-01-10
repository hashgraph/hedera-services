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

import com.swirlds.config.api.Configuration;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.logging.api.internal.level.MarkerState;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class HandlerLoggingLevelConfigTest {

    @Test
    void testConstructorExceptions() {
        Assertions.assertThrows(NullPointerException.class, () -> new HandlerLoggingLevelConfig(null));
        Assertions.assertThrows(NullPointerException.class, () -> new HandlerLoggingLevelConfig(null, (String) null));
    }

    @Test
    void testConstructor() {
        // given
        final HandlerLoggingLevelConfig config =
                new HandlerLoggingLevelConfig(getConfigBuilder().getOrCreateConfig());

        // then
        checkDefaultBehavior(config);
    }

    @Test
    void testWithDifferentPrefix() {
        // given
        final HandlerLoggingLevelConfig config =
                new HandlerLoggingLevelConfig(getConfigBuilder().getOrCreateConfig(), "test.prefix");

        // then
        checkDefaultBehavior(config);
    }

    @Test
    void testWithDifferentPrefixAndLevel() {
        // given
        final Configuration configuration =
                getConfigBuilder().withValue("logging.level", "ERROR").getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "test.prefix");

        // then
        checkEnabledForLevel(config, "", Level.ERROR);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.ERROR);
        checkEnabledForLevel(config, "com.sample", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.Class", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.ERROR);
    }

    @Test
    void testWithConfigPerClass() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "INFO")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .withValue("logging.level.com.sample.package.ClassCD", "DEBUG")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.TRACE);
        checkEnabledForLevel(config, "com.sample.package.ClassCD", Level.DEBUG);
    }

    @Test
    void testPackagesWithSimilarNames() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Bla", Level.DEBUG);
        checkEnabledForLevel(config, "com.samples.Class", Level.INFO);
    }

    private static void checkEnabledForLevel(HandlerLoggingLevelConfig config, String name, Level level) {
        Stream.of(Level.values())
                .filter(level::enabledLoggingOfLevel)
                .forEach(l -> Assertions.assertTrue(
                        config.isEnabled(name, l, null),
                        "%s should be enabled for package '%s' with level %s".formatted(l, name, level)));
        Stream.of(Level.values())
                .filter(l -> !level.enabledLoggingOfLevel(l))
                .forEach(l -> Assertions.assertFalse(
                        config.isEnabled(name, l, null),
                        "%s should not be enabled for package '%s' with level %s".formatted(l, name, level)));
    }

    @Test
    void testWithConfigUpdate() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "ERROR")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);

        // when
        final Configuration newConfiguration = getConfigBuilder()
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.sample.package", "WARN")
                .getOrCreateConfig();
        config.update(newConfiguration);

        // then
        checkEnabledForLevel(config, "", Level.ERROR);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.Class", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.WARN);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.WARN);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.WARN);
    }

    @Test
    void testWithConfigUpdateWitEmptyConfig() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "ERROR")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);

        // when
        final Configuration newConfiguration = getConfigBuilder().getOrCreateConfig();
        config.update(newConfiguration);

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Class", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.INFO);
    }

    @Test
    void testWithConfigAndDefaultLevel() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level", "WARN")
                .withValue("logging.level.com.sample", "DEBUG")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration);

        // then
        checkEnabledForLevel(config, "", Level.WARN);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.WARN);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.DEBUG);
    }

    @Test
    void testWithConfigAndDefaultLevelAndPrefix() {
        // given
        final Configuration configuration = getConfigBuilder()
                .withValue("logging.level.com.sample", "DEBUG")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "test.logging.level");

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.DEBUG);
    }

    private static void checkDefaultBehavior(HandlerLoggingLevelConfig config) {
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Class", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.INFO);

        Assertions.assertTrue(config.isEnabled(null, Level.ERROR, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.WARN, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.INFO, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.DEBUG, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.TRACE, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, null, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled("", null, null), "always return true if error happens");
    }

    private static TestConfigBuilder getConfigBuilder() {
        return new TestConfigBuilder()
                .withConverter(MarkerState.class, new MarkerStateConverter())
                .withConverter(ConfigLevel.class, new ConfigLevelConverter());
    }
}
