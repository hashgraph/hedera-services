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

package com.swirlds.config.impl.validators.annotation.internal;

import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.annotation.Negative;
import com.swirlds.config.impl.internal.ConfigNumberUtils;
import com.swirlds.config.impl.validators.DefaultConfigViolation;
import java.util.stream.Stream;

/**
 * A {@link ConfigValidator} implementation taht checks all config data objects for the constraints annotation
 * {@link Negative}
 */
public class NegativeConstraintsValidation implements ConfigValidator {

    @Override
    public Stream<ConfigViolation> validate(final Configuration configuration) {
        return ConfigReflectionUtils.getAllMatchingPropertiesForConstraintAnnotation(Negative.class, configuration)
                .stream()
                .filter(property -> ConfigNumberUtils.isNumber(property.propertyType()))
                .filter(property -> 0 <= ConfigNumberUtils.getLongValue(property.propertyValue()))
                .map(property -> new DefaultConfigViolation(
                        property.propertyName(), property.propertyValue() + "", true, "Value must be < 0"));
    }
}
