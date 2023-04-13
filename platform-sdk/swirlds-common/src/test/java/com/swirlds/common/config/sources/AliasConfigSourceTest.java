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

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class AliasConfigSourceTest {

    @Test
    void testEmptyParam() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new AliasConfigSource(null));
    }

    @Test
    void testOrdinal() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource();
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);

        // when
        Assertions.assertEquals(configSource.getOrdinal(), aliasConfigSource.getOrdinal());
    }

    @Test
    void testNoAlias() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1").withValue("b", "2");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("2", properties.get("b"));
    }

    @Test
    void testAlias() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);
        aliasConfigSource.addAlias("b", "a");

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("1", properties.get("b"));
    }

    @Test
    void testMultipleAliases() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);
        aliasConfigSource.addAlias("b", "a");
        aliasConfigSource.addAlias("c", "a");

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(3, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("1", properties.get("b"));
        Assertions.assertEquals("1", properties.get("c"));
    }

    @Test
    void testAliasForNotExistingProperty() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);
        aliasConfigSource.addAlias("b", "not-available");

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(1, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
    }

    @Test
    void testAliasesWithSameName() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1").withValue("b", "2");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);
        aliasConfigSource.addAlias("c", "a");
        aliasConfigSource.addAlias("c", "b");

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(3, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("2", properties.get("b"));
        Assertions.assertEquals("1", properties.get("c"));
    }

    @Test
    void testAliasesWithSameNameAsProperty() {
        // given
        final SimpleConfigSource configSource = new SimpleConfigSource("a", "1").withValue("b", "2");
        final AliasConfigSource aliasConfigSource = new AliasConfigSource(configSource);
        aliasConfigSource.addAlias("b", "a");

        // when
        final Map<String, String> properties = aliasConfigSource.getProperties();

        // then
        Assertions.assertNotNull(properties);
        Assertions.assertEquals(2, properties.keySet().size());
        Assertions.assertEquals("1", properties.get("a"));
        Assertions.assertEquals("2", properties.get("b"));
    }
}
