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

public class ConstraintMethodTest {

    @Test
    public void testNoViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("method.valueA", "true"))
                .withSources(new SimpleConfigSource("method.valueB", "true"))
                .withConfigDataTypes(ConstraintMethodConfigData.class);
        // then
        Assertions.assertDoesNotThrow(() -> configurationBuilder.build(), "No violation should happen");
    }

    @Test
    public void testViolation() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("method.valueA", "false"))
                .withSources(new SimpleConfigSource("method.valueB", "true"))
                .withConfigDataTypes(ConstraintMethodConfigData.class);

        // when
        final ConfigViolationException exception = Assertions.assertThrows(
                ConfigViolationException.class,
                () -> configurationBuilder.build(),
                "Violation for @ConstraintMethod should happen");

        // then
        Assertions.assertEquals(1, exception.getViolations().size());
        Assertions.assertTrue(exception.getViolations().get(0).propertyExists());
        Assertions.assertEquals(
                "method.checkA", exception.getViolations().get(0).getPropertyName());
        Assertions.assertEquals("false", exception.getViolations().get(0).getPropertyValue());
        Assertions.assertEquals("error", exception.getViolations().get(0).getMessage());
    }

    @Test
    public void testError() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("method.valueA", "true"))
                .withSources(new SimpleConfigSource("method.valueB", "false"))
                .withConfigDataTypes(ConstraintMethodConfigData.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "Error in validation should end in illegal state");
    }

    @Test
    public void testErrorInvalidMethod() {
        // given
        final ConfigurationBuilder configurationBuilder = ConfigurationBuilder.create()
                .withSources(new SimpleConfigSource("method.value", "true"))
                .withConfigDataTypes(BrokenConstraintMethodConfigData.class);

        // then
        Assertions.assertThrows(
                IllegalStateException.class,
                () -> configurationBuilder.build(),
                "Error in validation should end in illegal state");
    }
}
