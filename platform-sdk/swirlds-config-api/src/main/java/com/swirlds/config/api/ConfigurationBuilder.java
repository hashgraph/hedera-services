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

package com.swirlds.config.api;

import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.intern.ConfigurationProvider;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;

/**
 * The {@link ConfigurationBuilder} is the main entry point to the config api since it is used to create a
 * {@link Configuration} instance. A new builder can be created by calling {@link #create()} and must be used to setup a
 * configuration by adding {@link ConfigSource}, {@link ConfigConverter}, {@link ConfigValidator} or config data type
 * (see {@link ConfigData}) instance. By calling {@link #build()} a new {@link Configuration} instance will be created
 * based on the defined setup.
 */
public interface ConfigurationBuilder {

    /**
     * Adds a config source (see {@link ConfigSource}). If this method is called after the config has been created (see
     * {@link #build()}) a
     * {@link IllegalStateException} will be thrown.
     *
     * @param configSource
     * 		the config source that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withSource(final ConfigSource configSource) throws IllegalStateException;

    /**
     * Adds config sources (see {@link ConfigSource}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param configSources
     * 		the config sources that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withSources(final ConfigSource... configSources) throws IllegalStateException;

    /**
     * Adds a converter (see {@link ConfigConverter}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param converter
     * 		the converter that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withConverter(final ConfigConverter<?> converter) throws IllegalStateException;

    /**
     * Adds converters (see {@link ConfigConverter}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param converters
     * 		the converters that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withConverters(final ConfigConverter<?>... converters) throws IllegalStateException;

    /**
     * Adds a validator (see {@link ConfigValidator}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param validator
     * 		the validator that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withValidator(final ConfigValidator validator) throws IllegalStateException;

    /**
     * Adds validators (see {@link ConfigValidator}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param validators
     * 		the validators that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withValidators(final ConfigValidator... validators) throws IllegalStateException;

    /**
     * Adds a config data type (see {@link ConfigData}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param type
     * 		the config data type that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    <T extends Record> ConfigurationBuilder withConfigDataType(Class<T> type) throws IllegalStateException;

    /**
     * Adds config data types (see {@link ConfigData}). If this method is called after the config has been created (see
     * {@link #build()}) a {@link IllegalStateException} will be thrown.
     *
     * @param types
     * 		the config data types that should be used for the configuration
     * @return the builder instance (useful for fluent API)
     * @throws IllegalStateException
     * 		if this method is called after the config has been created
     */
    ConfigurationBuilder withConfigDataTypes(Class<? extends Record>... types) throws IllegalStateException;

    /**
     * Creates a {@link Configuration} instance based on this builder.
     *
     * @return a new {@link Configuration} instance
     * @throws IllegalStateException
     * 		If the method has already been called
     */
    Configuration build();

    /**
     * This is the main entry point to us the config API. By calling this method a new {@link ConfigurationBuilder} is
     * created that can be used to create a {@link Configuration}.
     *
     * @return a new {@link ConfigurationBuilder} instance
     */
    static ConfigurationBuilder create() {
        return ConfigurationProvider.getInstance().createBuilder();
    }
}
