/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.config.api.validation.annotation;

import com.swirlds.config.api.ConfigurationBuilder;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A constraint annotation that can be used define a min value for a config data property (see
 * {@link com.swirlds.config.api.ConfigProperty}). The validation of the annotation is automatically executed at the
 * initialization of the configuration (see {@link ConfigurationBuilder#build()}). The annotated property should not be
 * used for floating point values.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface Min {

    /**
     * Defines the minimum value that is allowed for the annotated number
     *
     * @return the minimum value that is allowed
     */
    long value();
}
