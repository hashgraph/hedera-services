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

package com.hedera.node.config.converter;

import static java.util.Objects.requireNonNull;

import com.hedera.node.config.types.PermissionedAccountsRange;
import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Implementation of the {@link ConfigConverter} api that adds support for the {@link PermissionedAccountsRange} type to
 * the config api. The method {@link PermissionedAccountsRange#from(String)} is used to parse the string value. Instead
 * of returning {@code null} when {@link PermissionedAccountsRange#from(String)} returns {@code null}, this converter
 * throws an {@code IllegalArgumentException} in that case.
 */
public final class PermissionedAccountsRangeConverter implements ConfigConverter<PermissionedAccountsRange> {

    /**
     * {@inheritDoc}
     */
    @Nullable
    @Override
    public PermissionedAccountsRange convert(@NonNull final String value)
            throws IllegalArgumentException, NullPointerException {
        requireNonNull(value, "'null' can not be converted");
        final var converted = PermissionedAccountsRange.from(value);
        if (converted == null) {
            throw new IllegalArgumentException("Invalid PermissionedAccountsRange value: " + value);
        }
        return converted;
    }
}
