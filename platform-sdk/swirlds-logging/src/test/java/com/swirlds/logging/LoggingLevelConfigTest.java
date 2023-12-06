/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
import com.swirlds.logging.api.internal.level.LoggingLevelConfig;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LoggingLevelConfigTest {

    @Test
    void testConstructorExceptions() {
        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null, (String) null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null, (Level) null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(configuration, (String) null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(configuration, (Level) null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null, "foo"));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null, null, null));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(null, "foo", Level.INFO));
        Assertions.assertThrows(
                NullPointerException.class, () -> new LoggingLevelConfig(configuration, null, Level.INFO));
        Assertions.assertThrows(NullPointerException.class, () -> new LoggingLevelConfig(configuration, "foo", null));
    }

    @Test
    void testConstructor() {
        // given
        final LoggingLevelConfig config = new LoggingLevelConfig(new TestConfigBuilder().getOrCreateConfig());

        // then
        checkDefaultBehavior(config);
    }

    @Test
    void testWithDifferentPrefix() {
        // given
        final LoggingLevelConfig config =
                new LoggingLevelConfig(new TestConfigBuilder().getOrCreateConfig(), "test.prefix");

        // then
        checkDefaultBehavior(config);
    }

    @Test
    void testWithDifferentPrefixAndLevel() {
        // given
        final LoggingLevelConfig config =
                new LoggingLevelConfig(new TestConfigBuilder().getOrCreateConfig(), "test.prefix", Level.ERROR);

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
    void testWithConfig() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "INFO")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration);

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.TRACE);
    }

    @Test
    void testWithBadConfig() {
        // given
        final Configuration configuration =
                new TestConfigBuilder().withValue("logging.level", "UNKNOWN").getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration);

        // then
        Assertions.assertTrue(config.isEnabled("", Level.ERROR));
    }

    private static void checkEnabledForLevel(LoggingLevelConfig config, String name, Level level) {
        List.of(Level.values()).stream()
                .filter(l -> level.enabledLoggingOfLevel(l))
                .forEach(l ->
                        Assertions.assertTrue(config.isEnabled(name, l), "'" + name + "' should be enabled for " + l));
        List.of(Level.values()).stream()
                .filter(l -> !level.enabledLoggingOfLevel(l))
                .forEach(l -> Assertions.assertFalse(
                        config.isEnabled(name, l), "'" + name + "' should not be enabled for " + l));
    }

    @Test
    void testWithConfigUpdate() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "ERROR")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration);

        // when
        final Configuration newConfiguration = new TestConfigBuilder()
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.sample.package", "WARN")
                .getOrCreateConfig();
        config.update(newConfiguration);

        // then
        checkEnabledForLevel(config, "", Level.ERROR);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.ERROR);
        checkEnabledForLevel(config, "com.sample", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.Class", Level.ERROR);
        checkEnabledForLevel(config, "com.sample.package", Level.WARN);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.WARN);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.WARN);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.WARN);
    }

    @Test
    void testWithConfigUpdateWitEmptyConfig() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level", "INFO")
                .withValue("logging.level.com.sample", "DEBUG")
                .withValue("logging.level.com.sample.package", "ERROR")
                .withValue("logging.level.com.sample.package.ClassB", "ERROR")
                .withValue("logging.level.com.sample.package.ClassC", "TRACE")
                .getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration);

        // when
        final Configuration newConfiguration = new TestConfigBuilder().getOrCreateConfig();
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
        final Configuration configuration = new TestConfigBuilder()
                .withValue("logging.level.com.sample", "DEBUG")
                .getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration, Level.WARN);

        // then
        checkEnabledForLevel(config, "", Level.WARN);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.WARN);
        checkEnabledForLevel(config, "com.sample", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.DEBUG);
    }

    @Test
    void testWithConfigAndDefaultLevelAndPrefix() {
        // given
        final Configuration configuration = new TestConfigBuilder()
                .withValue("test.logging.level.com.sample", "DEBUG")
                .getOrCreateConfig();
        final LoggingLevelConfig config = new LoggingLevelConfig(configuration, "test.logging.level");

        // then
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.Class", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.DEBUG);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.DEBUG);
    }

    private static void checkDefaultBehavior(LoggingLevelConfig config) {
        checkEnabledForLevel(config, "", Level.INFO);
        checkEnabledForLevel(config, "a.long.name.for.a.logger", Level.INFO);
        checkEnabledForLevel(config, "com.sample", Level.INFO);
        checkEnabledForLevel(config, "com.sample.Class", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassA", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassB", Level.INFO);
        checkEnabledForLevel(config, "com.sample.package.ClassC", Level.INFO);

        Assertions.assertTrue(config.isEnabled(null, Level.ERROR), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.WARN), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.INFO), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.DEBUG), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, Level.TRACE), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled(null, null), "always return true if error happens");
        Assertions.assertTrue(config.isEnabled("", null), "always return true if error happens");
    }
}
