/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.converter;

import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

public abstract class AbstractEnumConverter<T extends Enum<T>> implements ConfigConverter<T> {

    private final Class<T> enumType;

    public AbstractEnumConverter(@NonNull final Class<T> enumType) {
        this.enumType = Objects.requireNonNull(enumType, "enumType");
    }

    @Override
    @Nullable
    public T convert(@NonNull final String rawValue) throws IllegalArgumentException, NullPointerException {
        Objects.requireNonNull(rawValue, "rawValue");
        return Enum.valueOf(enumType, rawValue);
    }

}
