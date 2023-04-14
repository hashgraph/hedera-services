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

package com.hedera.node.app.spi.config.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.node.app.spi.config.internal.KeyValuePairConverter;
import com.hedera.node.app.spi.config.validation.internal.EmulatesMapValidator;
import com.swirlds.common.config.sources.SimpleConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.validation.ConfigViolation;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class EmulatesMapValidatorTest {

    @Test
    void testNullConstruction() {
        // given
        final Configuration configuration = null;
        final EmulatesMapValidator validator = new EmulatesMapValidator();

        // then
        assertThatThrownBy(() -> validator.validate(configuration)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEmptyConfig() {
        // given
        final Configuration configuration = ConfigurationBuilder.create().build();
        final EmulatesMapValidator validator = new EmulatesMapValidator();

        // when
        final Stream<ConfigViolation> validate = validator.validate(configuration);

        // then
        assertThat(validate).isNotNull().isEmpty();
    }

    @Test
    void testBasicUsage() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withConfigDataType(CorrectDefinedRecord.class)
                .withSource(new SimpleConfigSource("dataMap", "key1;value1,key2;value2"))
                .build();
        final EmulatesMapValidator validator = new EmulatesMapValidator();

        // when
        final Stream<ConfigViolation> violations = validator.validate(configuration);

        // then
        assertThat(violations).isNotNull().isEmpty();
    }

    @Test
    void testDoupleKeyUsage() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withConfigDataType(CorrectDefinedRecord.class)
                .withSource(new SimpleConfigSource("dataMap", "key1;value1,key1;value2"))
                .build();
        final EmulatesMapValidator validator = new EmulatesMapValidator();

        // when
        final List<ConfigViolation> violations =
                validator.validate(configuration).collect(Collectors.toList());

        // then
        assertThat(violations).isNotNull().hasSize(1).allMatch(violation -> violation
                .getPropertyName()
                .equals("dataMap"));
    }

    @Test
    void testWrongUsage() {
        // given
        final Configuration configuration = ConfigurationBuilder.create()
                .withConverter(new KeyValuePairConverter())
                .withConfigDataType(BadDefinedRecord.class)
                .withSource(new SimpleConfigSource("pair", "key1;value1"))
                .withSource(new SimpleConfigSource("data", "key1;value1"))
                .build();
        final EmulatesMapValidator validator = new EmulatesMapValidator();

        // when
        final List<ConfigViolation> violations =
                validator.validate(configuration).collect(Collectors.toList());

        // then
        assertThat(violations)
                .isNotNull()
                .hasSize(2)
                .anyMatch(violation -> violation.getPropertyName().equals("pair"))
                .anyMatch(violation -> violation.getPropertyName().equals("data"));
    }
}
