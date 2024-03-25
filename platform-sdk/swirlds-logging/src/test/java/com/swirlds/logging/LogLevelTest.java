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

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.base.test.fixtures.io.SystemErrProvider;
import com.swirlds.base.test.fixtures.io.WithSystemError;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import com.swirlds.logging.api.internal.level.ConfigLevel;
import com.swirlds.logging.api.internal.level.HandlerLoggingLevelConfig;
import com.swirlds.logging.api.internal.level.MarkerState;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@WithSystemError
public class LogLevelTest {

    @Inject
    private SystemErrProvider systemErrProvider;

    private TestConfigBuilder createDefaultBuilder() {
        return new TestConfigBuilder()
                .withConverter(MarkerState.class, new MarkerStateConverter())
                .withConverter(ConfigLevel.class, new ConfigLevelConverter());
    }

    @AfterEach
    void flushSystemError() {
        systemErrProvider.getLines().forEach(System.err::println);
    }

    @Test
    void logLevelWithHandlerOverwrite() {
        // given
        final Configuration configuration = createDefaultBuilder()
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.foo", "TRACE")
                .withValue("logging.level.com.bar", "TRACE")
                .withValue("logging.handler.HANDLER1.level.com.bar", "OFF")
                .withValue("logging.handler.HANDLER2.level.com.foo", "OFF")
                .withValue("logging.handler.HANDLER2.level", "INFO")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.bar.some.Class", Level.INFO, null)).isFalse();
        assertThat(config.isEnabled("com.bar.some.Class", Level.ERROR, null)).isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, null)).isTrue();
        assertThat(config.isEnabled("com.some.Class", Level.ERROR, null)).isTrue();
        assertThat(config.isEnabled("com.some.Class", Level.INFO, null)).isFalse();
    }

    @Test
    void logLevelWithHandlerOverwriteWithMarker() {
        // given
        final Marker markerBaz = new Marker("baz");
        final Marker markerBum = new Marker("bum");
        final Marker markerBrum = new Marker("brum");
        final Marker markerPeng = new Marker("peng");
        final Marker combinedMarker = new Marker("bum", markerBaz);

        final Configuration configuration = createDefaultBuilder()
                // Level
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.foo", "TRACE")
                .withValue("logging.level.com.bar", "TRACE")
                // Markers
                .withValue("logging.marker.baz", "ENABLED")
                .withValue("logging.marker.bum", "DISABLED")
                .withValue("logging.marker.peng", "UNDEFINED")
                // Handler Markers
                .withValue("logging.handler.HANDLER1.marker.peng", "DISABLED")
                .withValue("logging.handler.HANDLER2.marker.peng", "ENABLED")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.bar.some.Class", Level.INFO, markerBaz))
                .isTrue();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, markerBum))
                .isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, combinedMarker))
                .isTrue();
        assertThat(config.isEnabled("com.foo.some.Class", Level.ERROR, markerPeng))
                .isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, markerBrum))
                .isTrue();
        assertThat(config.isEnabled("com.some.Class", Level.ERROR, markerBrum)).isTrue();
        assertThat(config.isEnabled("com.some.Class", Level.INFO, markerBrum)).isFalse();
    }

    @Test
    void logLevelWithDifferentHandlers() {
        // given
        final Marker markerA = new Marker("A");
        final Marker markerB = new Marker("B");
        final Marker markerC = new Marker("C");

        final Configuration configuration = createDefaultBuilder()
                .withValue("logging.handler.HANDLER1.marker.A", "ENABLED")
                .withValue("logging.handler.HANDLER2.marker.C", "DISABLED")
                .getOrCreateConfig();

        final HandlerLoggingLevelConfig config1 = new HandlerLoggingLevelConfig(configuration, "HANDLER1");
        final HandlerLoggingLevelConfig config2 = new HandlerLoggingLevelConfig(configuration, "HANDLER2");

        // then
        assertThat(config1.isEnabled("com.example.ClassA", Level.INFO, markerA)).isTrue();
        assertThat(config1.isEnabled("com.example.ClassB", Level.ERROR, markerB))
                .isTrue();
        assertThat(config2.isEnabled("com.example.ClassC", Level.WARN, markerC)).isFalse();
        assertThat(config2.isEnabled("com.example.ClassA", Level.INFO, markerC)).isFalse();
    }

    @Test
    void logLevelWithDefaultHandler() {
        // given
        final Marker markerX = new Marker("X");
        final Marker markerY = new Marker("Y");

        final Configuration configuration = createDefaultBuilder()
                .withValue("logging.handler.HANDLER1.marker.X", "ENABLED")
                .withValue("logging.handler.HANDLER2.marker.Y", "DISABLED")
                .getOrCreateConfig();

        final HandlerLoggingLevelConfig config1 = new HandlerLoggingLevelConfig(configuration, "HANDLER1");
        final HandlerLoggingLevelConfig config2 = new HandlerLoggingLevelConfig(configuration, "HANDLER2");

        // then
        assertThat(config1.isEnabled("com.example.Class", Level.INFO, markerX)).isTrue();
        assertThat(config1.isEnabled("com.example.Class", Level.ERROR, markerY)).isTrue();
        assertThat(config2.isEnabled("com.example.Class", Level.DEBUG, markerY)).isFalse();
        assertThat(config2.isEnabled("com.example.Class", Level.TRACE, markerX)).isFalse();
    }

    @Test
    void logLevelWithInheritedHandler() {
        // given
        final Marker markerM = new Marker("M");

        final Configuration configuration = createDefaultBuilder()
                .withValue("logging.level", "OFF")
                .withValue("logging.handler.HANDLER1.marker.M", "ENABLED")
                .withValue("logging.handler.HANDLER2.marker.M", "UNDEFINED")
                .getOrCreateConfig();

        final HandlerLoggingLevelConfig config1 = new HandlerLoggingLevelConfig(configuration, "HANDLER1");
        final HandlerLoggingLevelConfig config2 = new HandlerLoggingLevelConfig(configuration, "HANDLER2");

        // then
        assertThat(config1.isEnabled("com.example.Class", Level.INFO, markerM)).isTrue();
        assertThat(config1.isEnabled("com.example.Class", Level.TRACE, markerM)).isTrue();
        assertThat(config2.isEnabled("com.example.Class", Level.ERROR, markerM)).isFalse();
        assertThat(config2.isEnabled("com.example.Class", Level.DEBUG, markerM)).isFalse();
    }

    @Test
    void nameNull() {
        // given
        final Configuration configuration =
                createDefaultBuilder().withValue("logging.level", "ERROR").getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // when
        final boolean result = config.isEnabled(null, Level.INFO, null);

        // then
        assertThat(result).isTrue();
        assertThat(systemErrProvider.getLines()).anyMatch(s -> s.contains("Null parameter: name"));
    }

    @Test
    void levelNull() {
        // given
        final Configuration configuration =
                createDefaultBuilder().withValue("logging.level", "ERROR").getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // when
        final boolean result = config.isEnabled("com.example", null, null);

        // then
        assertThat(result).isTrue();
        assertThat(systemErrProvider.getLines()).anyMatch(s -> s.contains("Null parameter: level"));
    }

    @Test
    void logLevelMarkerOverwrite() {
        // given
        final Marker markerA = new Marker("A");

        final Configuration configuration = createDefaultBuilder()
                .withValue("logging.marker.A", "DISABLED")
                .withValue("logging.handler.HANDLER1.marker.A", "ENABLED")
                .getOrCreateConfig();

        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.example.ClassA", Level.INFO, markerA)).isTrue();
    }

    @Test
    void logLevelNoInheritanceForLevels() {
        // given
        final Marker markerBaz = new Marker("baz");

        final Configuration configuration = createDefaultBuilder()
                // Level
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.foo", "TRACE")
                .withValue("logging.level.com.bar", "TRACE")
                // Markers
                .withValue("logging.marker.baz", "DISABLED")
                // Handler Markers
                .withValue("logging.handler.HANDLER1.level", "OFF")
                .withValue("logging.handler.HANDLER1.inheritLevels", "FALSE")
                .withValue("logging.handler.HANDLER1.marker.baz", "ENABLED")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.bar.some.Class", Level.INFO, markerBaz))
                .isTrue();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, null)).isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.ERROR, null)).isFalse();
    }

    @Test
    void logLevelNoInheritanceForLevelsDefaultLevel() {
        // given
        final Marker markerBaz = new Marker("baz");

        final Configuration configuration = createDefaultBuilder()
                // Level
                .withValue("logging.level", "ERROR")
                .withValue("logging.level.com.foo", "TRACE")
                .withValue("logging.level.com.bar", "TRACE")
                // Markers
                .withValue("logging.marker.baz", "DISABLED")
                // Handler Markers
                .withValue("logging.handler.HANDLER1.inheritLevels", "FALSE")
                .withValue("logging.handler.HANDLER1.marker.baz", "ENABLED")
                .getOrCreateConfig();
        final HandlerLoggingLevelConfig config = new HandlerLoggingLevelConfig(configuration, "HANDLER1");

        // then
        assertThat(config.isEnabled("com.bar.some.Class", Level.INFO, markerBaz))
                .isTrue();
        assertThat(config.isEnabled("com.foo.some.Class", Level.TRACE, null)).isFalse();
        assertThat(config.isEnabled("com.foo.some.Class", Level.INFO, null)).isTrue();
    }
}
