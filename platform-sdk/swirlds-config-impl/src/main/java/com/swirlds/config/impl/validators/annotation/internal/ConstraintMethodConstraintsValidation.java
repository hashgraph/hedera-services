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
import com.swirlds.config.api.validation.annotation.ConstraintMethod;
import java.lang.reflect.Method;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * A {@link ConfigValidator} implementation taht checks all config data objects for the constraints annotation
 * {@link ConstraintMethod}
 */
public class ConstraintMethodConstraintsValidation implements ConfigValidator {

    @Override
    public Stream<ConfigViolation> validate(final Configuration configuration) {
        return ConfigReflectionUtils.getAllMatchingPropertiesForConstraintAnnotation(
                        ConstraintMethod.class, configuration)
                .stream()
                .map(property -> execute(configuration, property))
                .filter(Objects::nonNull);
    }

    private static ConfigViolation execute(
            final Configuration configuration,
            final ConfigReflectionUtils.AnnotatedProperty<ConstraintMethod, ?> annotatedProperty) {
        try {
            final String methodName = annotatedProperty.annotation().value();
            final Class<Record> recordType =
                    (Class<Record>) annotatedProperty.component().getDeclaringRecord();
            final Object recordInstance = configuration.getConfigData(recordType);
            final Method method =
                    annotatedProperty.component().getDeclaringRecord().getMethod(methodName, Configuration.class);
            final Object violation = method.invoke(recordInstance, configuration);
            if (violation == null) {
                return null;
            }
            if (violation instanceof ConfigViolation configViolation) {
                return configViolation;
            }
            throw new IllegalStateException("Validation failed since method '" + methodName + " in record '"
                    + recordType + "' does not " + "follow"
                    + " " + "the definition pattern 'public " + ConfigViolation.class.getName() + " method("
                    + Configuration.class.getName() + " config)'");
        } catch (final Exception e) {
            throw new IllegalStateException("Validation failed!", e);
        }
    }
}
