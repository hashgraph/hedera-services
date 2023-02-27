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

package com.swirlds.config.impl.internal;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Stream;

final class ConstraintValidator implements ConfigValidator {

    private final ConverterService converterService;

    @SuppressWarnings("rawtypes")
    private final Queue<ConfigPropertyConstraintData> constraintData;

    ConstraintValidator(final ConverterService converterService) {
        this.converterService = CommonUtils.throwArgNull(converterService, "converterService");
        this.constraintData = new ConcurrentLinkedQueue<>();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Stream<ConfigViolation> validate(final Configuration configuration) {
        CommonUtils.throwArgNull(configuration, "configuration");
        return constraintData.stream()
                .map(d -> {
                    PropertyMetadata<?> propertyMetadata = createMetadata(d.propertyName, d.valueType, configuration);
                    return d.validator.check(propertyMetadata);
                })
                .filter(Objects::nonNull);
    }

    private <T> PropertyMetadata<T> createMetadata(
            final String propertyName, final Class<T> valueType, final Configuration configuration) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(valueType, "valueType");
        CommonUtils.throwArgNull(configuration, "configuration");
        if (configuration.exists(propertyName)) {
            final ConfigConverter<T> converter = converterService.getConverterForType(valueType);
            return new PropertyMetadataImpl<>(
                    propertyName, configuration.getValue(propertyName), valueType, true, converter);
        } else {
            final ConfigConverter<T> converter = converterService.getConverterForType(valueType);
            return new PropertyMetadataImpl<>(propertyName, null, valueType, false, converter);
        }
    }

    <T> void addConstraint(
            final String propertyName, final Class<T> valueType, final ConfigPropertyConstraint<T> validator) {
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(valueType, "valueType");
        CommonUtils.throwArgNull(validator, "validator");
        constraintData.add(new ConfigPropertyConstraintData<>(propertyName, valueType, validator));
    }

    void clear() {
        constraintData.clear();
    }

    private record ConfigPropertyConstraintData<T>(
            String propertyName, Class<T> valueType, ConfigPropertyConstraint<T> validator) {}
}
