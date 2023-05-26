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

package com.hedera.node.config.converter;

import static com.swirlds.common.utility.CommonUtils.unhex;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Converts a string of hex digits into a {@link Bytes} object.
 */
public class BytesConverter implements ConfigConverter<Bytes> {
    @Nullable
    @Override
    public Bytes convert(@NonNull String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("BytesConverter cannot convert null value");
        } else if (!value.startsWith("0x")) {
            throw new IllegalArgumentException("Invalid format. Value must start with '0x': " + value);
        } else {
            try {
                final var hex = value.substring(2);
                final var bytes = unhex(hex);
                return Bytes.wrap(bytes);
            } catch (final Exception e) {
                throw new IllegalArgumentException("Invalid format: " + value, e);
            }
        }
    }
}
