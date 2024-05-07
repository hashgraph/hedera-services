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
 * Annotation that indicates an unset value for a property in a config data object. A config data object is typically a {@link Record}
 * that provides access to config values in an object-oriented way (see {@link ConfigData} for more information).
 * <p>
 * The {@code UnsetValue} annotation can be used to represent cases where a property in the config data object does not have a
 * defined value. This annotation is useful when handling optional properties where the absence of a value has semantic meaning.
 * </p>
 * <p>
 * This annotation can be particularly useful for representing Java's default types as unset values. For example, an {@code int} property
 * annotated with {@code UnsetValue} may represent an optional integer value that defaults to Java's default value of 0 if unset.
 * </p>
 * <p>
 * Rules for Java types:
 * <ul>
 *     <li>{@code byte}, {@code short}, {@code int}, {@code long}: Default to 0.</li>
 *     <li>{@code float}, {@code double}: Default to 0.0.</li>
 *     <li>{@code boolean}: Default to {@code false}.</li>
 *     <li>{@code char}: Default to '\u0000' (null character).</li>
 *     <li>{@code Object}: Default to {@code null}.</li>
 *     <li>{@code array types}: Default to {@code null}.</li>
 * </ul>
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * public record ServerConfig(
 *         &#64;UnsetValue String host,
 *         int port,
 *         &#64;UnsetValue int timeout) {
 * }
 * </pre>
 * In this example, both {@code host} and {@code timeout} property are annotated with {@code UnsetValue}, indicating that they are optional and may
 * not have a defined value in the config data object. {@code host} will be mapped to null and {@code timeOut} to zero.
 * By the contrary {@code port} property will have to be informed in the property file or {@link IllegalArgumentException} will be thrown on start-up time.
 * </p>
 * <p>
 * This is a syntax sugar version of:
 * <pre>
 * &#64;DefaultValue({})
 * </pre>
 * @see DefaultValue
 * @see EmptyValue
 */
@Documented
@Retention(RUNTIME)
@Inherited
@DefaultValue(value = {})
public @interface UnsetValue {}
