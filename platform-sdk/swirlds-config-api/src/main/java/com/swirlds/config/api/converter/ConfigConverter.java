/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.api.converter;

/**
 * Interface that provides the functionality to convert a raw {@link String} value of a property to a specific data
 * type.
 * <p>
 * Implementations of the interface can be added to the configuration setup by calling
 * {@link com.swirlds.config.api.ConfigurationBuilder#withConverter(ConfigConverter)}
 *
 * @param <T>
 * 		The data type of the converter
 */
@FunctionalInterface
public interface ConfigConverter<T> {

    /**
     * The method that is called to convert the given raw string value to a specific data type
     *
     * @param value
     * @return the converted value
     * @throws IllegalArgumentException
     * 		if the given String value can not be converted
     * @throws NullPointerException
     * 		if the given String value is null
     */
    T convert(String value) throws IllegalArgumentException, NullPointerException;
}
