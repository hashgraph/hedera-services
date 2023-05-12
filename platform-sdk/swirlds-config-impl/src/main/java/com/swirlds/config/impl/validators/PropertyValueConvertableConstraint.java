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

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;

/**
 * Implementation of {@link ConfigPropertyConstraint} that results in a violation if the value of the property can not be
 * converted.
 *
 * @param <T>
 * 		type of the value
 */
public class PropertyValueConvertableConstraint<T> implements ConfigPropertyConstraint<T> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigViolation check(final PropertyMetadata<T> metadata) {
        CommonUtils.throwArgNull(metadata, "metadata");
        if (!metadata.exists()) {
            final String message = "Property '" + metadata.getName() + "' must be defined";
            return DefaultConfigViolation.of(metadata, message);
        }
        final ConfigConverter<T> converter = metadata.getConverter();
        if (converter == null) {
            final String message = "No converter for type '" + metadata.getValueType() + "' + of property '"
                    + metadata.getName() + "'" + " defined";
            return DefaultConfigViolation.of(metadata, message);
        }
        try {
            converter.convert(metadata.getRawValue());
        } catch (final Exception e) {
            final String message = "Value '" + metadata.getRawValue() + "' of property '" + metadata.getName() + "' "
                    + "can not be converted to '" + metadata.getValueType() + "'";
            return DefaultConfigViolation.of(metadata, message);
        }
        return null;
    }
}
