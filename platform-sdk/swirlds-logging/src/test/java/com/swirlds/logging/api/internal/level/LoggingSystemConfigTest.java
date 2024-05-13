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

package com.swirlds.logging.api.internal.level;

import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.logging.api.Level;
import com.swirlds.logging.api.Marker;
import com.swirlds.logging.api.internal.configuration.ConfigLevelConverter;
import com.swirlds.logging.api.internal.configuration.MarkerStateConverter;
import org.junit.jupiter.api.Test;

class LoggingSystemConfigTest {
    @Test
    void test() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isTrue();
    }

    @Test
    void test2() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level", "debug")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
    }

    @Test
    void test3() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level", "debug")
                .withValue("logging.handler.console.level.com", "info")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF)).isFalse();
    }

    @Test
    void test4() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.level.com.swirlds", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "false") // should also test when this is true
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level", "debug")
                .withValue("logging.handler.console.level.com", "info")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com.swirlds", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.swirlds", Level.DEBUG)).isFalse();
    }

    @Test
    void defaultLoggingLevelShouldApplyIfNotInformed() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level.com.swirlds", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level.com", "info")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
    }

    @Test
    void defaultLoggingLevelShouldApplyIfNotInformedNoInheritLevels() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level.com.swirlds", "trace")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "false")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level.com", "info")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
    }

    @Test
    void test5() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "error")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.level.com.A", "trace")
                .withValue("logging.handler.console2.type", "console")
                .withValue("logging.handler.console2.enabled", "true")
                .withValue("logging.handler.console2.inheritLevels", "true")
                .withValue("logging.handler.console2.formatTimestamp", "true")
                .withValue("logging.handler.console2.level.com.B", "trace")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(2);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE)).isTrue();
    }

    @Test
    void test6() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "error")
                .withValue("logging.marker.MARKER", "enabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        Marker marker = new Marker("MARKER");
        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker)).isTrue();
    }

    @Test
    void test7() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "off")
                .withValue("logging.marker.MARKER", "enabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.marker.MARKER2", "enabled")
                .withValue("logging.handler.console2.type", "console")
                .withValue("logging.handler.console2.enabled", "true")
                .withValue("logging.handler.console2.inheritLevels", "true")
                .withValue("logging.handler.console2.formatTimestamp", "true")
                .withValue("logging.handler.console2.marker.MARKER3", "enabled")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(2);

        Marker marker = new Marker("MARKER");
        Marker marker2 = new Marker("MARKER2");
        Marker marker3 = new Marker("MARKER3");
        Marker markerNotPresent = new Marker("MARKER_NOT_PRESENT");

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker2)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker3)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.TRACE));
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.TRACE));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.TRACE));
    }

    @Test
    void marker_isEnabled_atLeastOneIsEnabled() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "off")
                .withValue("logging.marker.MARKER", "enabled")
                .withValue("logging.marker.MARKER2", "disabled")
                .withValue("logging.marker.MARKER3", "disabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "false")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .build();

        Marker marker =
                new Marker("MARKER", new Marker("MARKER2", new Marker("MARKER3", new Marker("MARKER_NOT_PRESENT"))));

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isTrue();
    }

    @Test
    void marker_isEnabled_allEnabledEnabled() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "off")
                .withValue("logging.marker.MARKER", "enabled")
                .withValue("logging.marker.MARKER2", "enabled")
                .withValue("logging.marker.MARKER3", "enabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "false")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .build();

        Marker marker =
                new Marker("MARKER", new Marker("MARKER2", new Marker("MARKER3", new Marker("MARKER_NOT_PRESENT"))));

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isTrue();
    }

    @Test
    void marker_isEnabled_allDisabled() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "trace")
                .withValue("logging.marker.MARKER", "disabled")
                .withValue("logging.marker.MARKER2", "disabled")
                .withValue("logging.marker.MARKER3", "disabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "false")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .build();

        Marker marker =
                new Marker("MARKER", new Marker("MARKER2", new Marker("MARKER3", new Marker("MARKER_NOT_PRESENT"))));

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(1);

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isFalse();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isFalse();
    }

    @Test
    void test9() {
        final com.swirlds.config.api.Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new ConfigLevelConverter())
                .withConverter(new MarkerStateConverter())
                .withValue("logging.level", "off")
                .withValue("logging.marker.MARKER", "enabled")
                .withValue("logging.marker.MARKER2", "disabled")
                .withValue("logging.marker.MARKER3", "disabled")
                .withValue("logging.handler.console.type", "console")
                .withValue("logging.handler.console.enabled", "true")
                .withValue("logging.handler.console.inheritLevels", "true")
                .withValue("logging.handler.console.formatTimestamp", "true")
                .withValue("logging.handler.console.marker.MARKER2", "enabled")
                .withValue("logging.handler.console2.type", "console")
                .withValue("logging.handler.console2.enabled", "true")
                .withValue("logging.handler.console2.inheritLevels", "true")
                .withValue("logging.handler.console2.formatTimestamp", "true")
                .withValue("logging.handler.console2.marker.MARKER3", "enabled")
                .build();

        LoggingSystemConfig loggingSystemConfig = new LoggingSystemConfig(configuration);
        assertThat(loggingSystemConfig.getLogHandlers()).hasSize(2);

        Marker marker = new Marker("MARKER");
        Marker marker2 = new Marker("MARKER2");
        Marker marker3 = new Marker("MARKER3");
        Marker markerNotPresent = new Marker("MARKER_NOT_PRESENT");

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker2)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker2)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, marker3)).isTrue();
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, marker3)).isTrue();

        assertThat(loggingSystemConfig.isEnabled("", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("", Level.TRACE));
        assertThat(loggingSystemConfig.isEnabled("com", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("com", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com", Level.OFF));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.OFF, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com.A", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.A", Level.TRACE));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.ERROR, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.ERROR));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.WARN, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.WARN));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.DEBUG, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.DEBUG));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.INFO, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.INFO));
        assertThat(loggingSystemConfig.isEnabled("com.B", Level.TRACE, markerNotPresent))
                .isEqualTo(loggingSystemConfig.isEnabled("com.B", Level.TRACE));
    }
}
