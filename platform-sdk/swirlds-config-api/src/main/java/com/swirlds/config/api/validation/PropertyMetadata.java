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

package com.swirlds.config.api.validation;

import com.swirlds.config.api.converter.ConfigConverter;

/**
 * Interface that provides metadata for a property
 *
 * @param <T>
 * 		type of the property value
 */
public interface PropertyMetadata<T> {

    /**
     * Returns the raw value of the property
     *
     * @return the raw value of the property
     */
    String getRawValue();

    /**
     * Returns the value of the property
     *
     * @return the value of the property
     */
    default T getValue() {
        return getConverter().convert(getRawValue());
    }

    /**
     * Returns the type of the property value
     *
     * @return the type of the property value
     */
    Class<T> getValueType();

    /**
     * Returns the converter that will be used to convert the raw string value of the property to the value of the
     * property type.
     *
     * @return the converter
     */
    ConfigConverter<T> getConverter();

    /**
     * Returns true if the property exists (is defined by a {@link com.swirlds.config.api.source.ConfigSource})
     *
     * @return true if the property exists, false otherwise
     */
    boolean exists();

    /**
     * Returns the name of the property
     *
     * @return the names
     */
    String getName();
}
