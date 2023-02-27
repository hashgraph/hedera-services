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

package com.swirlds.config.impl.internal;

import com.swirlds.common.utility.CommonUtils;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.validation.ConfigPropertyConstraint;
import com.swirlds.config.api.validation.ConfigValidator;
import com.swirlds.config.api.validation.ConfigViolation;
import com.swirlds.config.api.validation.ConfigViolationException;
import com.swirlds.config.impl.validators.annotation.internal.ConstraintMethodConstraintsValidation;
import com.swirlds.config.impl.validators.annotation.internal.MaxConstraintsValidation;
import com.swirlds.config.impl.validators.annotation.internal.MinConstraintsValidation;
import com.swirlds.config.impl.validators.annotation.internal.NegativeConstraintsValidation;
import com.swirlds.config.impl.validators.annotation.internal.PositiveConstraintsValidation;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Service for validation of the config
 */
class ConfigValidationService implements ConfigLifecycle {

    /**
     * Set opf all validators that shpuld be executed on a validation
     */
    private final Queue<ConfigValidator> validators;

    /**
     * A specific validator that checks all {@link ConfigPropertyConstraint} instances
     */
    private final ConstraintValidator constraintValidator;

    /**
     * Defines if this service is initialized
     */
    private boolean initialized = false;

    ConfigValidationService(final ConverterService converterService) {
        this.constraintValidator = new ConstraintValidator(converterService);
        this.validators = new ConcurrentLinkedQueue<>();
    }

    void addValidator(final ConfigValidator validator) {
        throwIfInitialized();
        CommonUtils.throwArgNull(validator, "validator");
        validators.add(validator);
    }

    public void init() {
        throwIfInitialized();
        // General Validator for constraints
        validators.add(constraintValidator);
        validators.add(new ConstraintMethodConstraintsValidation());
        validators.add(new MaxConstraintsValidation());
        validators.add(new MinConstraintsValidation());
        validators.add(new NegativeConstraintsValidation());
        validators.add(new PositiveConstraintsValidation());
        initialized = true;
    }

    @Override
    public void dispose() {
        validators.clear();
        constraintValidator.clear();
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    void validate(final Configuration configuration) {
        throwIfNotInitialized();
        CommonUtils.throwArgNull(configuration, "configuration");
        final List<ConfigViolation> violations =
                validators.stream().flatMap(v -> v.validate(configuration)).collect(Collectors.toList());
        if (!violations.isEmpty()) {
            final int violationCount = violations.size();
            throw new ConfigViolationException(
                    "Configuration failed based on " + violationCount + " violations!", violations);
        }
    }

    <T> void addConstraint(
            final String propertyName, final Class<T> valueType, final ConfigPropertyConstraint<T> validator) {
        throwIfInitialized();
        CommonUtils.throwArgBlank(propertyName, "propertyName");
        CommonUtils.throwArgNull(valueType, "valueType");
        CommonUtils.throwArgNull(validator, "validator");
        constraintValidator.addConstraint(propertyName, valueType, validator);
    }
}
