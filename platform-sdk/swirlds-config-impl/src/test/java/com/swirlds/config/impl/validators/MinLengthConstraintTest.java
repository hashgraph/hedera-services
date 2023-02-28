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

package com.swirlds.config.impl.validators;

import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MinLengthConstraintTest {

    @Test
    public void testPassNull() {
        // given
        final MinLengthConstraint constraint = new MinLengthConstraint(7);
        final PropertyMetadata<String> metadata = null;

        // then
        Assertions.assertThrows(IllegalArgumentException.class, () -> constraint.check(metadata));
    }

    @Test
    public void testValid() {
        // given
        final MinLengthConstraint constraint = new MinLengthConstraint(7);
        final String propName = "app.property";
        final String value = "12345678";
        final Class<String> propertyClass = String.class;
        final boolean present = true;
        final ConfigConverter<String> converter = v -> v;
        final PropertyMetadata<String> metadata =
                new DummyMetadata<>(propName, value, propertyClass, present, converter);

        // when
        final ConfigViolation configViolation = constraint.check(metadata);

        // then
        Assertions.assertNull(configViolation);
    }

    @Test
    public void testContrainsNoProperty() {
        // given
        final MinLengthConstraint constraint = new MinLengthConstraint(7);
        final String propName = "app.property";
        final String value = null;
        final Class<String> propertyClass = String.class;
        final boolean present = false;
        final ConfigConverter<String> converter = v -> v;
        final PropertyMetadata<String> metadata =
                new DummyMetadata<>(propName, value, propertyClass, present, converter);

        // when
        final ConfigViolation configViolation = constraint.check(metadata);

        // then
        Assertions.assertNotNull(configViolation);
        Assertions.assertEquals("app.property", configViolation.getPropertyName());
        Assertions.assertNull(configViolation.getPropertyValue());
        Assertions.assertFalse(configViolation.propertyExists());
    }

    @Test
    public void testContrainsPropertyValueNull() {
        // given
        final MinLengthConstraint constraint = new MinLengthConstraint(7);
        final String propName = "app.property";
        final String value = null;
        final Class<String> propertyClass = String.class;
        final boolean present = true;
        final ConfigConverter<String> converter = v -> v;
        final PropertyMetadata<String> metadata =
                new DummyMetadata<>(propName, value, propertyClass, present, converter);

        // when
        final ConfigViolation configViolation = constraint.check(metadata);

        // then
        Assertions.assertNotNull(configViolation);
        Assertions.assertEquals("app.property", configViolation.getPropertyName());
        Assertions.assertNull(configViolation.getPropertyValue());
        Assertions.assertTrue(configViolation.propertyExists());
    }

    @Test
    public void testInvalid() {
        // given
        final MinLengthConstraint constraint = new MinLengthConstraint(7);
        final String propName = "app.property";
        final String value = "12345";
        final Class<String> propertyClass = String.class;
        final boolean present = true;
        final ConfigConverter<String> converter = v -> v;
        final PropertyMetadata<String> metadata =
                new DummyMetadata<>(propName, value, propertyClass, present, converter);

        // when
        final ConfigViolation configViolation = constraint.check(metadata);

        // then
        Assertions.assertNotNull(configViolation);
        Assertions.assertEquals("app.property", configViolation.getPropertyName());
        Assertions.assertEquals("12345", configViolation.getPropertyValue());
        Assertions.assertTrue(configViolation.propertyExists());
    }
}
