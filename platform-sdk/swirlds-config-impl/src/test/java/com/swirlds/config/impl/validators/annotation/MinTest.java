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

package com.swirlds.config.impl.validators.annotation;

import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MinTest {

    @Test
    public void testNoViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("min.intValue", 3))
                .withSources(new SimpleConfigSource("min.longValue", 3L))
                .withSources(new SimpleConfigSource("min.doubleValue", 3D))
                .withSources(new SimpleConfigSource("min.floatValue", 3F))
                .withSources(new SimpleConfigSource("min.shortValue", 3))
                .withSources(new SimpleConfigSource("min.byteValue", 3))
                .withConfigDataTypes(MinTestConfigData.class);

        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "No violation should happen");
    }

    @Test
    public void testIntViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("min.intValue", 1))
                .withSources(new SimpleConfigSource("min.longValue", 3L))
                .withSources(new SimpleConfigSource("min.doubleValue", 3D))
                .withSources(new SimpleConfigSource("min.floatValue", 3F))
                .withSources(new SimpleConfigSource("min.shortValue", 3))
                .withSources(new SimpleConfigSource("min.byteValue", 3))
                .withConfigDataTypes(MinTestConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "Violation for @Min should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals("min.intValue", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("1", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals(
                "Value must be >= 2", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testViolations() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("min.intValue", 1))
                .withSources(new SimpleConfigSource("min.longValue", 1L))
                .withSources(new SimpleConfigSource("min.doubleValue", 1D))
                .withSources(new SimpleConfigSource("min.floatValue", 1F))
                .withSources(new SimpleConfigSource("min.shortValue", 1))
                .withSources(new SimpleConfigSource("min.byteValue", 1))
                .withConfigDataTypes(MinTestConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class, () -> configurationBuilder.build(), "Violation for @Min should happen");

        // then
        Assertions.assertEquals(4, exception.getViolations().size());
    }

    @Test
    public void testEdgeCaseViolations() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("min.intValue", 2))
                .withSources(new SimpleConfigSource("min.longValue", 2L))
                .withSources(new SimpleConfigSource("min.doubleValue", 2D))
                .withSources(new SimpleConfigSource("min.floatValue", 2F))
                .withSources(new SimpleConfigSource("min.shortValue", 2))
                .withSources(new SimpleConfigSource("min.byteValue", 2))
                .withConfigDataTypes(MinTestConfigData.class);

        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "No violation should happen");
    }
}
