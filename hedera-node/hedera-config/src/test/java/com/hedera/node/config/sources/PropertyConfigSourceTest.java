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

import static org.junit.jupiter.api.Assertions.*;

import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PropertyConfigSourceTest {

    private Properties properties;

    @BeforeEach
    void setUp() {
        properties = new Properties();
        properties.setProperty("key1", "value1");
        properties.setProperty("key2", "value2");
    }

    @Test
    void testExistentPropertyConfigSource() {
        // given
        int ordinal = 1; // used to determine priority of config source

        // when
        PropertyConfigSource propertyConfigSource = new PropertyConfigSource(properties, ordinal);

        // then
        assertEquals(2, propertyConfigSource.getPropertyNames().size());
        assertEquals("value1", propertyConfigSource.getValue("key1"));
        assertEquals("value2", propertyConfigSource.getValue("key2"));
        assertEquals(ordinal, propertyConfigSource.getOrdinal());
    }

    @Test
    void testPropertyConfigSourceWhenPropertyFileNotFound() {
        // given
        String nonExistentFile = "non-existent-file.properties";

        // then
        assertThrows(UncheckedIOException.class, () -> new PropertyConfigSource(nonExistentFile, 1));
    }

    @Test
    void testLoadProperties() throws Exception {
        // given
        String existingFile = "alphabet.properties";

        // when
        Method method = PropertyConfigSource.class.getDeclaredMethod("loadProperties", String.class);
        method.setAccessible(true);

        // then
        // Test successful path
        Properties properties = (Properties) method.invoke(null, existingFile);
        assertNotNull(properties);
    }

    @AfterEach
    void tearDown() {
        properties = null;
    }
}
