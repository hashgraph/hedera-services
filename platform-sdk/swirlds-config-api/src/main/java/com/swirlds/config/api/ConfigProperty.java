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

import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be used to annotate properties for a config data object. A config data object is a {@link Record}
 * that provides access to config values in an object-oriented way (see {@link ConfigData} for more information).
 * <p>
 * Example:
 * <pre>
 * &#64;ConfigData("network")
 * public record NetworkConfig(&#64;ConfigProperty("portNumber") int port,
 *                             &#64;ConfigProperty("serverName") String server) {
 * }
 * </pre>
 * In this example, the {@code port} and {@code server} values can easily be accessed by calling the record instance
 * (see {@link Configuration#getConfigData(Class)} for more information). The property name of the {@code port} property will
 * be {@code "network.portNumber"} and the property name of the {@code server} property will be {@code "network.serverName"}.
 * </p>
 * <p>
 * If any of Java's keywords are used in the config file, and you want to map that property in a record, {@code ConfigProperty} can be used
 *  to remap the property name in the record. For example:
 * <pre>
 * &#64;ConfigData("config")
 * public record Configuration(&#64;ConfigProperty("class") String className) {
 * }
 * </pre>
 * In this example, the property name in the config file is "class", which is a Java keyword. By using {@code ConfigProperty},
 * we can map it to a different name in the record, such as "className".
 * </p>
 * <p>
 * The {@code defaultValue} attribute of this annotation is deprecated and will be removed in future versions. To assign default values to properties, use {@link DefaultValue}
 * or its syntax sugared versions {@link EmptyValue} or {@link UnsetValue}.
 * </p>
 * <p>
 * This annotation is not mandatory. If it is not set, the name of the property in the config file must match the name of
 * the property in the record.
 */
@Documented
@Retention(RUNTIME)
@Target(RECORD_COMPONENT)
public @interface ConfigProperty {

    /**
     * A constant that is used to check if a default is defined.
     * @deprecated use {@link UnsetValue} instead.
     */
    @Deprecated
    String UNDEFINED_DEFAULT_VALUE = "com.swirlds.config.value.unconfiguredValue#1j8!-235u-hBHJ-#nxs-!n2n";

    /**
     * A constant that is used to check if a default is defined as null.
     * @deprecated for obtaining a null value do not set {@link DefaultValue},
     */
    @Deprecated
    String NULL_DEFAULT_VALUE = "com.swirlds.config.value.nullValue#1j8!-235u-hBHJ-#nxs-!n2n";

    /**
     * This value is used to define the name of the property. the prefix of the property name is defined by the
     * {@link ConfigData} annotation and the suffix is defined by this value while the concrete property name will be
     * {@code PREFIX.SUFFIX}
     *
     * @return the suffix of the name
     */
    String value() default "";

    /**
     * Returns a default value for the property that will be used if the value of the property is not defined in the
     * config. Since an empty string is a valid value {@link ConfigProperty#UNDEFINED_DEFAULT_VALUE} is used as default
     * for this value.
     *
     * @return the default value or {@link ConfigProperty#UNDEFINED_DEFAULT_VALUE}
     * @deprecated This annotation is meant to remap the name of the property in the record to a different property name in the file
     *      i.e: if any of the java's keyword is used in the file.
     *      {@code ConfigProperty#defaultValue()} should not be used to assign default values to the properties, and we should
     *      favor usage of {@link DefaultValue} or it's syntax sugared versions {@link EmptyValue} or {@link UnsetValue}
     */
    @Deprecated(forRemoval = true)
    String defaultValue() default UNDEFINED_DEFAULT_VALUE;
}
