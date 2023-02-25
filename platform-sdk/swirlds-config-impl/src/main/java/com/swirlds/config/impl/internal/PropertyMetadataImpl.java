/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.validation.PropertyMetadata;

/**
 * Implementation of the {@link PropertyMetadata} interface
 *
 * @param name
 * 		the property name
 * @param value
 * 		the property value
 * @param valueType
 * 		the property type
 * @param present
 * 		true if the property is present
 * @param converter
 * 		the converter for the property
 * @param <T>
 * 		the type
 */
public record PropertyMetadataImpl<T>(
        String name, String value, Class<T> valueType, boolean present, ConfigConverter<T> converter)
        implements PropertyMetadata<T> {
    @Override
    public String getRawValue() {
        return value();
    }

    @Override
    public Class<T> getValueType() {
        return valueType();
    }

    @Override
    public ConfigConverter<T> getConverter() {
        return converter();
    }

    @Override
    public boolean exists() {
        return present();
    }

    @Override
    public String getName() {
        return name();
    }
}
