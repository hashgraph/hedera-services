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

package com.hedera.node.app.spi.config.validation.internal;

import com.hedera.node.app.spi.config.types.KeyValuePair;
import com.hedera.node.app.spi.config.validation.EmulatesMap;
import com.swirlds.common.config.reflection.ConfigReflectionUtils;
import com.swirlds.common.config.reflection.ConfigReflectionUtils.AnnotatedProperty;
import com.swirlds.common.config.validators.DefaultConfigViolation;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A {@link ConfigValidator} that validates any property annotated with {@link EmulatesMap}. See {@link EmulatesMap} for
 * more details.
 */
public class EmulatesMapValidator implements ConfigValidator {

    @Override
    @NonNull
    public Stream<ConfigViolation> validate(@NonNull final Configuration configuration) {
        Objects.requireNonNull(configuration, "Configuration cannot be null");
        return ConfigReflectionUtils.getAllMatchingPropertiesForConstraintAnnotation(EmulatesMap.class, configuration)
                .stream()
                .map(this::convert)
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @NonNull
    private Optional<ConfigViolation> convert(@NonNull final AnnotatedProperty<EmulatesMap, ?> property) {
        Objects.requireNonNull(property, "property cannot be null");
        final Class<?> type = property.propertyType();
        if (!List.class.isAssignableFrom(type) && !Set.class.isAssignableFrom(type)) {
            final ConfigViolation violation =
                    create(property, "Property is annotated with @EmulatesMap but is not a Set or List");
            return Optional.of(violation);
        }
        final Type genericType = property.component().getGenericType();
        if (genericType instanceof ParameterizedType parameterizedType) {
            final Type singleGenericTypeArgument =
                    ConfigReflectionUtils.getSingleGenericTypeArgument(parameterizedType);
            if (!KeyValuePair.class.equals(singleGenericTypeArgument)) {
                final ConfigViolation violation = create(
                        property, "Property is annotated with @EmulatesMap but is not a Collection of KeyValuePair");
                return Optional.of(violation);
            }
        } else {
            final ConfigViolation violation =
                    create(property, "Property is annotated with @EmulatesMap but is not a Collection of KeyValuePair");
            return Optional.of(violation);
        }
        final var typedValue = (AnnotatedProperty<EmulatesMap, Collection<KeyValuePair>>) property;

        final long uniqueKeyCount =
                typedValue.propertyValue().stream().map(p -> p.key()).distinct().count();
        if (uniqueKeyCount != typedValue.propertyValue().size()) {
            final ConfigViolation violation = create(typedValue, "Property contains duplicate keys");
            return Optional.of(violation);
        }
        return Optional.empty();
    }

    @NonNull
    private ConfigViolation create(
            @NonNull final AnnotatedProperty<EmulatesMap, ?> property, @NonNull final String message) {
        Objects.requireNonNull(property, "AnnotatedProperty cannot be null");
        Objects.requireNonNull(message, "Message cannot be null");
        return new DefaultConfigViolation(property.propertyName(), "VALUE_NOT_AVAILABLE", true, message);
    }
}
