/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;

/**
 * Annotation that represents an empty value for a property in a config data object. A config data object is typically a {@link Record}
 * that provides access to config values in an object-oriented way (see {@link ConfigData} for more information).
 * <p>
 * The {@code EmptyValue} annotation can be used to represent cases where a property in the config data object has a defined value
 * that is considered empty or unset. This annotation is useful when handling properties that can have an empty value with semantic meaning.
 * </p>
 * <p>
 * This annotation can be particularly useful for representing Java's default types as empty values. For example, an {@code int} property
 * annotated with {@code EmptyValue} may represent an optional integer value that defaults to Java's default value of 0 if empty.
 * </p>
 * <p>
 * Rules for Java types:
 * <ul>
 *     <li>{@code char}: Default to '' (empty character).</li>
 *     <li>{@code String}: Default to empty string ({@code ""}).</li>
 *     <li>{@code Collection types} (e.g., {@code List}, {@code Set}): Default to an empty collection.</li>
 *     <li>The behaviour for other types is undefined.</li>
 * </ul>
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * public record ServerConfig(
 *         String host,
 *         int port,
 *         &#64;EmptyValue List<String> blockedIps) {
 * }
 * </pre>
 * In this example, the {@code blockedIps} property is annotated with {@code EmptyValue}, indicating that it is optional and may
 * have an empty list in the config data object.
 * </p>
 * This is a syntax sugar version of:
 * <pre>
 * &#64;DefaultValue({""})
 * </pre>
 * @see DefaultValue
 * @see UnsetValue
 */
@Documented
@Retention(RUNTIME)
@Inherited
@DefaultValue(value = {""})
public @interface EmptyValue {}
