/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MappedConfigSourceTest {

    @Test
    void testEmptyParam() {
        Assertions.assertThrows(NullPointerException.class, () -> new MappedConfigSource(null));
    }

    @Test
    void testOrdinal() {
        // given
        final var configSource = new SimpleConfigSource();
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        Assertions.assertEquals(configSource.getOrdinal(), mappedConfigSource.getOrdinal());
    }

    @Test
    void testNoAlias() {
        // given
        final var configSource = new SimpleConfigSource().withValue("a", "1").withValue("b", "2");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        final var properties = mappedConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("2", properties.get("b"));
    }

    @Test
    void testAlias() {
        // given
        final var configSource = new SimpleConfigSource("a", "1");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        mappedConfigSource.addMapping("b", "a");
        final var properties = mappedConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("1", properties.get("b"));
    }

    @Test
    void testMultipleAliases() {
        // given
        final var configSource = new SimpleConfigSource("a", "1");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        mappedConfigSource.addMapping("b", "a");

        // then
        Assertions.assertThrows(IllegalArgumentException.class, () -> mappedConfigSource.addMapping("c", "a"));
    }

    @Test
    void testAliasForNotExistingProperty() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1");
        final MappedConfigSource mappedConfigSource = new MappedConfigSource(configSource);

        // when
        mappedConfigSource.addMapping("b", "not-available");

        // then
        Assertions.assertEquals(1, mappedConfigSource.getProperties().size());
        Assertions.assertEquals(Set.of("a"), mappedConfigSource.getPropertyNames());
    }

    @Test
    void testAliasesWithSameName() {
        // given
        final var configSource = new SimpleConfigSource("a", "1").withValue("b", "2");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        mappedConfigSource.addMapping("c", "a");

        // then
        Assertions.assertThrows(IllegalArgumentException.class, () -> mappedConfigSource.addMapping("c", "b"));
    }

    @Test
    void testWithConfiguration() {
        // given
        final var configSource = new SimpleConfigSource("a", "1");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        mappedConfigSource.addMapping("foo.a", "a");

        final var config =
                new TestConfigBuilder().withSource(mappedConfigSource).getOrCreateConfig();

        // when
        final var valueFromOldName = config.getValue("a");
        final var valueFromNewName = config.getValue("foo.a");

        // then
        Assertions.assertEquals(valueFromOldName, valueFromNewName);
    }

    @Test
    void testNoMappingForEmptySource() {
        // given
        final var configSource = new SimpleConfigSource();
        final var mappedConfigSource = new MappedConfigSource(configSource);

        mappedConfigSource.addMapping("foo.a", "a");

        // when
        final var config =
                new TestConfigBuilder().withSource(mappedConfigSource).getOrCreateConfig();

        // then
        Assertions.assertThrows(NoSuchElementException.class, () -> config.getValue("a"));
        Assertions.assertThrows(NoSuchElementException.class, () -> config.getValue("foo.a"));
    }

    @Test
    void testDefaultValuesForEmptySource() {
        // given
        final var configSource = new SimpleConfigSource();
        final var mappedConfigSource = new MappedConfigSource(configSource);
        mappedConfigSource.addMapping("foo.a", "a");

        final var config =
                new TestConfigBuilder().withSource(mappedConfigSource).getOrCreateConfig();

        // when
        final var oldName = config.getValue("a", "1");
        final var newName = config.getValue("foo.a", "2");

        // then
        Assertions.assertEquals("1", oldName);
        Assertions.assertEquals("2", newName);
    }

    @Test
    void testNoMappingIfAlreadyInSource() {
        // given
        final var configSource = new SimpleConfigSource("a", "1").withValue("foo.a", "2");
        final var mappedConfigSource = new MappedConfigSource(configSource);

        // when
        mappedConfigSource.addMapping("foo.a", "a");
        final var properties = mappedConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("2", properties.get("foo.a"));
    }
}
