/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import java.util.Objects;

/**
 * Implementation of {@code ConfigConverter} specifically for converting enums.
 */
public class EnumConverter<T extends Enum<T>> implements ConfigConverter<T> {

    private final Class<T> valueType;

    /**
     * @param valueType enum type this converter is associated to
     * @throws NullPointerException if {@code valueType} is {@code null}
     */
    public EnumConverter(@NonNull Class<T> valueType) {
        this.valueType = Objects.requireNonNull(valueType, "valueType must not be null");
    }

    /**
     * @param value String value to be converted to a valid enum instance
     * @return the enum instance associated with {@code value}
     * @throws IllegalArgumentException if value is not a valid enum value for {@code valueType}
     * @throws NullPointerException     if {@code value} is {@code null}
     */
    @Nullable
    @Override
    public T convert(@NonNull String value) throws IllegalArgumentException, NullPointerException {
        try {
            return Enum.valueOf(this.valueType, Objects.requireNonNull(value, "value must not be null"));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Can not convert value '%s' of Enum '%s' by default. Please add a custom config converter."
                            .formatted(value, this.valueType),
                    e);
        }
    }
}
