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

package com.hedera.node.app.spi.config.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A constraint annotation that can be used to define a map-like structure for a config data property (see {@link
 * com.swirlds.config.api.ConfigProperty}). The annotated property should be a {@link java.util.List} of {@link
 * com.hedera.node.app.spi.config.types.KeyValuePair}. By adding the annotation a {@link
 * com.swirlds.config.api.validation.ConfigViolation} will be thrown if the {@link java.util.List} value of the property
 * contains 2 or more {@link com.hedera.node.app.spi.config.types.KeyValuePair} with the same key.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface EmulatesMap {}
