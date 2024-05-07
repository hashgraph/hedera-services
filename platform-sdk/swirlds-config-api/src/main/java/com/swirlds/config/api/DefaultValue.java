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

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that specifies a default value for a property in a config data object. A config data object is typically a {@link Record}
 * that provides access to config values in an object-oriented way (see {@link ConfigData} for more information).
 * <p>
 * The {@code DefaultValue} annotation can be used to specify a default value for a property when it is not explicitly set in the
 * config property file. This annotation is useful for ensuring that properties have a fallback value if not explicitly provided in the config.
 * </p>
 * <p>
 * This annotation allows specifying only one default value, although the {@code value} property is an array.
 * The main difference with {@link ConfigProperty#defaultValue()} is that it does not have a default annotation property value for {@code value} and it needs to be informed.
 * </p>
 * <p>
 * If the record is used without any of the versions of {@code DefaultValue}, the property will be considered mandatory, and an exception may be thrown if the value is not found in the config.
 * </p>
 * <p>
 * This annotation cannot be used in parallel with {@link ConfigProperty#defaultValue()}. If both
 * annotations coexist for the same property, the behavior is undefined, and it may lead to unexpected results.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * public record ServerConfig(
 *         String host,
 *         int port,
 *         &#64;DefaultValue("5000") int timeout) {
 * }
 * </pre>
 * In this example, the {@code timeout} property is annotated with {@code DefaultValue}, specifying a default value of "5000" milliseconds.
 * If no value is provided in the config data object, the timeout property will default to "5000" milliseconds. Both {@code host} and {@code port} need to exist in the config property file.
 * </p>
 * <p>
 *
 *  To set an undefined value for a property:
 * <pre>
 * public record ServerConfig(
 *         &#64;DefaultValue({}) String host,
 *         &#64;DefaultValue({}) int port,
 *         &#64;DefaultValue({}) short timeout,
 *         &#64;DefaultValue({}) List<String> blockedIps) {
 * }
 * </pre>
 * By setting {@code &#64;DefaultValue({})} the value of the property will be handled as java default's type values.
 * In this example both {@code host} and {@code blockedIps} will be mapped to null, and both {@code port} and {@code timeOut} are mapped to0.
 * </p>
 * <p>
 * To set an empty value for a property:
 * <pre>
 * public record ServerConfig(
 *         &#64;DefaultValue("") String host,
 *         int port,
 *         short timeout,
 *         &#64;DefaultValue("") List<String> blockedIps) {
 * }
 * </pre>
 * By setting {@code &#64;DefaultValue("")} the value of the property will be handled as java default's type values.
 * In this example {@code host} will be mapped to an empty String and {@code blockedIps} will be mapped to an empty list.
 * </p>
 * <p>
 * Syntax Sugar Version:
 * To provide a more concise syntax, {@link UnsetValue} or {@link EmptyValue} annotations can be used instead of {@code DefaultValue} in any of the above 2 examples.
 * All these annotations imply that the property is optional and does not need to be explicitly provided in the config.
 * Only one of them can be used for a single Record's property or {@link IllegalArgumentException} will be thrown.
 * </p>
 * @see UnsetValue
 * @see EmptyValue
 */
@Documented
@Retention(RUNTIME)
@Inherited
@Target({RECORD_COMPONENT, ANNOTATION_TYPE})
public @interface DefaultValue {

    /**
     * This value is used to define default value of the property.
     *
     * @return the default value
     */
    String[] value();
}
