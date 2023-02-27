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
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.PropertyMetadata;

/**
 * Implementation of {@link ConfigPropertyConstraint} that results in a violation if the property value has less
 * characters than the defined length.
 */
public class MinLengthConstraint implements ConfigPropertyConstraint<String> {

    private final int minLength;

    /**
     * Creates the constraint
     *
     * @param minLength
     * 		the minmal allowed length
     */
    public MinLengthConstraint(final int minLength) {
        this.minLength = minLength;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConfigViolation check(final PropertyMetadata<String> metadata) {
        CommonUtils.throwArgNull(metadata, "metadata");
        if (!metadata.exists()) {
            final String message = "Property '" + metadata.getName() + "' must be defined";
            return DefaultConfigViolation.of(metadata, message);
        }
        if (metadata.getRawValue() == null) {
            final String message = "Property '" + metadata.getName() + "' must not be null.";
            return DefaultConfigViolation.of(metadata, message);
        }
        final int valueLength = metadata.getRawValue().length();
        if (valueLength < minLength) {
            final String message = "String value of Property '" + metadata.getName() + "' must have a minimum "
                    + "length of '" + minLength + "'";
            return DefaultConfigViolation.of(metadata, message);
        }
        return null;
    }
}
