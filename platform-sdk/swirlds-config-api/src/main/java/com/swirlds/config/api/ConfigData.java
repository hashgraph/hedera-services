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

package com.swirlds.config.api;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be used to annotate a config data object. A config data object is a {@link Record} that provides
 * access to config values in an object-oriented way.
 * <p>
 * Example:
 * <pre>
 * &#64;ConfigData("network")
 * public record NetworkConfig(int port,
 *                             String server) {
 * }
 * </pre>
 * In this example the {@code port} and {@code server} values can easily be accessed by calling the record instance (see
 * {@link Configuration#getConfigData(Class)} for more infos).  The property name of the {@code port} property will be
 * {@code "network.port"} and the property name of the {@code server} property will be {@code "network.server"}
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface ConfigData {

    /**
     * Defines the prefix for the property names that are part of the annotated record / config data object.
     *
     * @return the prefix for the property names
     */
    String value() default "";
}
