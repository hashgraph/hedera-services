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

package com.swirlds.common.config.sources;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.swirlds.config.api.source.ConfigSource;
import java.io.CharArrayWriter;
import java.util.Map;
import java.util.NoSuchElementException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.StringLayout;
import org.apache.logging.log4j.core.appender.WriterAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Testing RemappedConfigSource")
class RemappedConfigSourceTest {

    @Test
    @DisplayName("Testing constructor with legal properties and mappings")
    void constructorShouldRemapPropertiesByMappings() {
        // given
        final ConfigSource source =
                new SimpleConfigSource().withValue("foo", 1).withValue("bar", 2).withValue("new.baz", 3);
        final Map<String, String> mappings = Map.of(
                "foo", "new.foo",
                "bar", "new.bar",
                "baz", "new.baz");

        // when
        final RemappedConfigSource remappedSource = new RemappedConfigSource(source, mappings);
        final Map<String, String> remappedKeys = remappedSource.getRemappedPropertyKeys();

        // then
        assertThat(remappedSource.getProperties()).hasSize(3);
        assertThat(remappedKeys).contains(entry("new.foo", "foo"), entry("new.bar", "bar"), entry("new.baz", "baz"));
        assertThatThrownBy(() -> remappedSource.getValue("foo")).isInstanceOf(NoSuchElementException.class);
        assertThat(remappedSource.getValue("new.foo")).isEqualTo("1");
        assertThatThrownBy(() -> remappedSource.getValue("bar")).isInstanceOf(NoSuchElementException.class);
        assertThat(remappedSource.getValue("new.bar")).isEqualTo("2");
        assertThatThrownBy(() -> remappedSource.getValue("baz")).isInstanceOf(NoSuchElementException.class);
        assertThat(remappedSource.getValue("new.baz")).isEqualTo("3");
    }

    @Test
    @DisplayName("Testing constructor with illegal properties")
    void constructorShouldThrowExceptionWithIllegalProperties() {
        final ConfigSource source = new SimpleConfigSource()
                .withValue("foo", 1)
                .withValue("new.foo", 2)
                .withValue("new.baz", 3);
        final Map<String, String> mappings = Map.of(
                "foo", "new.foo",
                "bar", "new.bar",
                "baz", "new.baz");

        assertThatThrownBy(() -> new RemappedConfigSource(source, mappings)).isInstanceOf(IllegalConfigException.class);
    }

    @Test
    @DisplayName("Testing constructor with illegal mappings")
    void constructorShouldNotRemapWithIllegalMappings() {
        final ConfigSource source =
                new SimpleConfigSource().withValue("foo", 1).withValue("bar", 2).withValue("baz", 3);
        final Map<String, String> mappings = Map.of(
                "foo", "new.foo",
                "bar", "new.foo",
                "baz", "baz");

        assertThatThrownBy(() -> new RemappedConfigSource(source, mappings)).isInstanceOf(IllegalConfigException.class);
    }

    @Test
    @DisplayName("Testing constructor with empty mappings")
    void constructorShouldNotRemapAnyPropertiesWithEmptyMappings() {
        // given
        final ConfigSource source =
                new SimpleConfigSource().withValue("foo", 1).withValue("bar", 2).withValue("new.baz", 3);
        final Map<String, String> mappings = Map.of();

        // when
        final RemappedConfigSource remappedSource = new RemappedConfigSource(source, mappings);
        final Map<String, String> remappedKeys = remappedSource.getRemappedPropertyKeys();

        // then
        assertThat(remappedSource.getProperties()).hasSize(3);
        assertThat(remappedKeys).isEmpty();
        assertThat(remappedSource.getValue("foo")).isEqualTo("1");
        assertThat(remappedSource.getValue("bar")).isEqualTo("2");
        assertThat(remappedSource.getValue("new.baz")).isEqualTo("3");
    }

    @Test
    @DisplayName("Testing constructor with null parameters")
    void constructorShouldThrowExceptionWithNullParameters() {
        final ConfigSource source = new SimpleConfigSource().withValue("foo", 1);
        final Map<String, String> mappings = Map.of();

        assertThatThrownBy(() -> new RemappedConfigSource(null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RemappedConfigSource(null, mappings)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RemappedConfigSource(source, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Testing logRemappedProperties")
    @Disabled("Run this in manual mode with log4j2-test.xml")
    void logRemappedPropertiesShouldLogWarnMessageIfRemapped() {
        // setup
        final Logger logger = (Logger) LogManager.getLogger(RemappedConfigSource.class);
        final StringLayout layout =
                PatternLayout.newBuilder().withPattern("%-5level %msg").build();
        final CharArrayWriter outContent = new CharArrayWriter();

        final Appender appender = WriterAppender.newBuilder()
                .setTarget(outContent)
                .setLayout(layout)
                .setName("testAppender")
                .build();
        appender.start();
        logger.addAppender(appender);

        // given
        final ConfigSource source = new SimpleConfigSource().withValue("foo", 1).withValue("bar", 2);
        final Map<String, String> mappings = Map.of("foo", "new.foo");

        // when
        new RemappedConfigSource(source, mappings);

        // then
        assertThat(outContent.toString()).contains("WARN", "foo", "new.foo");
        assertThat(outContent.toString()).doesNotContain("bar", "new.bar");

        // teardown
        logger.removeAppender(appender);
    }
}
