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

package com.hedera.node.config.sources;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DynamicConfigSourceTest {

    @Test
    void testEmpty() {
        // given
        final DynamicConfigSource dynamicConfigSource = new DynamicConfigSource();

        // then
        assertThat(dynamicConfigSource).returns(500, DynamicConfigSource::getOrdinal);
        assertThat(dynamicConfigSource.getPropertyNames()).isEmpty();
    }

    @Test
    void testAddProperty() {
        // given
        final DynamicConfigSource dynamicConfigSource = new DynamicConfigSource();

        // when
        dynamicConfigSource.setProperty("test", "testValue");

        // then
        assertThat(dynamicConfigSource.getPropertyNames()).isNotEmpty();
        assertThat(dynamicConfigSource.getPropertyNames()).contains("test");
        assertThat(dynamicConfigSource.getValue("test")).isEqualTo("testValue");
    }

    @Test
    void testSetProperty() {
        // given
        final DynamicConfigSource dynamicConfigSource = new DynamicConfigSource();
        dynamicConfigSource.setProperty("test", "testValue");

        // when
        dynamicConfigSource.setProperty("test", "testValue2");

        // then
        assertThat(dynamicConfigSource.getPropertyNames()).isNotEmpty();
        assertThat(dynamicConfigSource.getPropertyNames()).contains("test");
        assertThat(dynamicConfigSource.getValue("test")).isEqualTo("testValue2");
    }

    @Test
    void testRemoveProperty() {
        // given
        final DynamicConfigSource dynamicConfigSource = new DynamicConfigSource();
        dynamicConfigSource.setProperty("test", "testValue");

        // when
        dynamicConfigSource.removeProperty("test");

        // then
        assertThat(dynamicConfigSource.getPropertyNames()).isEmpty();
    }
}
