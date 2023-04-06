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

import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Central interface to provide configuration properties. The API provides different methods how properties can be
 * accessed.
 */
public interface Configuration {

    /**
     * A constant that can be used to define an empty list.
     */
    String EMPTY_LIST = "[]";

    /**
     * Returns a {@link Stream} of all available property names
     *
     * @return the stream of all property names
     */
    Stream<String> getPropertyNames();

    /**
     * Checks if a property with the given name exists and returns true in that case.
     *
     * @param propertyName the name of the property that should be checked
     * @return true if the property exists, false otherwise
     */
    boolean exists(String propertyName);

    /**
     * Returns the {@link String} value of the property with the given name.
     *
     * @param propertyName the name of the property
     * @return the value of the property
     * @throws NoSuchElementException if the property does not exist.
     */
    String getValue(final String propertyName) throws NoSuchElementException;

    /**
     * Returns the {@link String} value of the property with the given name or the given default value if the property
     * does not exist.
     *
     * @param propertyName the name of the property
     * @param defaultValue the default value that will be used if the property does not exist.
     * @return the value of the property or the given default value if the property does not exist
     */
    String getValue(final String propertyName, String defaultValue);

    /**
     * Returns the value of the property with the given name.
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the property
     * @param <T>          the generic type of the property
     * @return the value of the property
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@code String} value of the property can not be converted to the
     *                                  given type
     */
    <T> T getValue(String propertyName, Class<T> propertyType) throws NoSuchElementException, IllegalArgumentException;

    /**
     * Returns the value of the property with the given name or the given default value if the property does not exist.
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the property
     * @param defaultValue the default value that will be used if the property does not exist.
     * @param <T>          the generic type of the property
     * @return the value of the property or the given default value if the property does not exist
     * @throws IllegalArgumentException if the raw {@code String} value of the property can not be converted to the
     *                                  given type
     */
    <T> T getValue(String propertyName, Class<T> propertyType, T defaultValue) throws IllegalArgumentException;

    /**
     * Returns a {@link List} of string elements of the property with the given name
     *
     * @param propertyName the name of the property
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    List<String> getValues(String propertyName);

    /**
     * Returns a {@link List} of string elements of the property with the given name or the given default {@link List}
     *
     * @param propertyName the name of the property
     * @param defaultValue the default {@link List}
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    List<String> getValues(String propertyName, List<String> defaultValue);

    /**
     * Returns a {@link List} of elements of the property with the given name
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the elements
     * @param <T>          the generic type of the elements
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    <T> List<T> getValues(String propertyName, Class<T> propertyType)
            throws NoSuchElementException, IllegalArgumentException;

    /**
     * Returns a {@link List} of elements of the property with the given name or the given default {@link List}
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the elements
     * @param defaultValue the default {@link List}
     * @param <T>          the generic type of the elements
     * @return a {@link List} of elements of the property with the given name or the given default list
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    <T> List<T> getValues(String propertyName, Class<T> propertyType, List<T> defaultValue)
            throws IllegalArgumentException;

    /**
     * Returns a {@link List} of string elements of the property with the given name
     *
     * @param propertyName the name of the property
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    Set<String> getValueSet(String propertyName);

    /**
     * Returns a {@link List} of string elements of the property with the given name or the given default {@link List}
     *
     * @param propertyName the name of the property
     * @param defaultValue the default {@link List}
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    Set<String> getValueSet(String propertyName, Set<String> defaultValue);

    /**
     * Returns a {@link List} of elements of the property with the given name
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the elements
     * @param <T>          the generic type of the elements
     * @return a {@link List} of elements of the property with the given name
     * @throws NoSuchElementException   if the property does not exist.
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    <T> Set<T> getValueSet(String propertyName, Class<T> propertyType)
            throws NoSuchElementException, IllegalArgumentException;

    /**
     * Returns a {@link List} of elements of the property with the given name or the given default {@link List}
     *
     * @param propertyName the name of the property
     * @param propertyType the type of the elements
     * @param defaultValue the default {@link List}
     * @param <T>          the generic type of the elements
     * @return a {@link List} of elements of the property with the given name or the given default list
     * @throws IllegalArgumentException if the raw {@link String} value of the property can not be converted to a list
     *                                  or the given type
     */
    <T> Set<T> getValueSet(String propertyName, Class<T> propertyType, Set<T> defaultValue)
            throws IllegalArgumentException;

    /**
     * Returns a config data object of the given type. This is used to provide an object-oriented construct for
     * accessing config properties for a specific usecase. By doing so all configuration values for a specific service
     * can for example be stored in an immutable record instance that is used to access the values.
     * <p>
     * The given type defines the {@link Record} that defines the config properties. The annotations {@link ConfigData}
     * and {@link ConfigProperty} can be used in the record definition to specify the names and default values of
     * properties.The result of this method is an instance of the given {@link Record} that provides the values for all
     * properties that are specified in the record definition.
     *
     * @param type type of the record
     * @param <T>  type of the record
     * @return instance of the record that contains the config values for all config properties that are defined in the
     * record definition.
     */
    <T extends Record> T getConfigData(Class<T> type);

    /**
     * Returns all types that are registered as config data types (see {@link #getConfigData(Class)}).
     *
     * @return all types that are registered as config data types
     */
    Collection<Class<? extends Record>> getConfigDataTypes();
}
