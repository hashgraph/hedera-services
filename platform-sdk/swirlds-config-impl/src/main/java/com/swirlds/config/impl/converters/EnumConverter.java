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

package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public class EnumConverter<T> implements ConfigConverter<T> {

    private final Class<T> valueType;

    public EnumConverter(Class<T> valueType) {
        this.valueType = valueType;
    }

    @Nullable
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public T convert(@NonNull String value) throws IllegalArgumentException, NullPointerException {
        try {
            return (T) Enum.valueOf((Class<Enum>) valueType, value);
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Can not convert value '%s' of Enum '%s' by default. Please add a custom config converter."
                            .formatted(value, valueType),
                    e);
        }
    }
}
