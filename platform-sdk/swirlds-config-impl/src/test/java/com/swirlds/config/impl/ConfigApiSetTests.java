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

package com.swirlds.config.impl;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConfigApiSetTests {

    @Test
    public void readListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource().withIntegerValues("testNumbers", List.of(1, 2, 3)))
                .build();

        // when
        final Set<Integer> values = configuration.getValueSet("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(3, values.size(), "A property that is defined as set should be parsed correctly");
        Assertions.assertTrue(
                values.contains(1), "A property that is defined as set should contain the defined values");
        Assertions.assertTrue(
                values.contains(2), "A property that is defined as set should contain the defined values");
        Assertions.assertTrue(
                values.contains(3), "A property that is defined as set should contain the defined values");
    }

    @Test
    public void readListPropertyWithOneEntry() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", 123))
                .build();

        // when
        final Set<Integer> values = configuration.getValueSet("testNumbers", Integer.class);

        // then
        Assertions.assertEquals(1, values.size(), "A property that is defined as set should be parsed correctly");
        Assertions.assertTrue(
                values.contains(123), "A property that is defined as set should contain the defined values");
    }

    @Test
    public void readBadListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", "1,2,   3,4"))
                .build();

        // then
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> configuration.getValueSet("testNumbers", Integer.class),
                "given set property should not be parsed correctly");
    }

    @Test
    public void readDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final Set<Integer> values = configuration.getValueSet("testNumbers", Integer.class, Set.of(6, 7, 8));

        // then
        Assertions.assertEquals(
                3, values.size(), "The default value should be used since no value is defined by the config");
        Assertions.assertTrue(values.contains(6), "Should be part of the set since it is part of the default");
        Assertions.assertTrue(values.contains(7), "Should be part of the set since it is part of the default");
        Assertions.assertTrue(values.contains(8), "Should be part of the set since it is part of the default");
    }

    @Test
    public void readNullDefaultListProperty() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // when
        final Set<Integer> values = configuration.getValueSet("testNumbers", Integer.class, null);

        // then
        Assertions.assertNull(values, "Null should be a valid default value");
    }

    @Test
    public void checkListPropertyImmutable() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new SimpleConfigSource("testNumbers", "1,2,3"))
                .build();

        // when
        final Set<Integer> values = configuration.getValueSet("testNumbers", Integer.class);

        // then
        Assertions.assertThrows(
                UnsupportedOperationException.class, () -> values.add(10), "Set properties should always be immutable");
    }

    @Test
    public void testNotDefinedEmptyList() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();

        // then
        Assertions.assertThrows(NoSuchElementException.class, () -> configuration.getValueSet("sample.list"));
        Assertions.assertThrows(
                NoSuchElementException.class, () -> configuration.getValueSet("sample.list", String.class));
        Assertions.assertThrows(
                NoSuchElementException.class, () -> configuration.getValueSet("sample.list", Integer.class));
    }
}
