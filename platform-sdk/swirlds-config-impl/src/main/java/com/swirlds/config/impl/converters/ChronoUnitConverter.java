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

package com.swirlds.config.impl.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Objects;

/**
 * Converter for type {@link ChronoUnit}
 */
public class ChronoUnitConverter implements ConfigConverter<ChronoUnit> {

    /**
     * {@inheritDoc}
     */
    @Override
    public ChronoUnit convert(final String value) throws IllegalArgumentException {
        if (value == null) {
            throw new NullPointerException("null can not be converted");
        }
        return Arrays.stream(ChronoUnit.values())
                .filter(u -> Objects.equals(u.toString().toLowerCase(), value.toLowerCase()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can not parse ChronoUnit: " + value));
    }
}
