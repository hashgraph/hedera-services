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

package com.swirlds.logging.util;

import com.swirlds.config.api.Configuration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

public class SimpleConfiguration implements Configuration {

    private final Map<String, String> properties = new ConcurrentHashMap<>();

    public void setProperty(final String name, final String value) {
        properties.put(name, value);
    }

    public SimpleConfiguration withProperty(final String name, final String value) {
        setProperty(name, value);
        return this;
    }

    @Override
    public Stream<String> getPropertyNames() {
        return properties.keySet().stream();
    }

    @Override
    public boolean exists(final String s) {
        return properties.containsKey(s);
    }

    @Override
    public String getValue(final String s) throws NoSuchElementException {
        return properties.get(s);
    }

    @Override
    public String getValue(final String s, final String s1) {
        return Optional.ofNullable(properties.get(s)).orElse(s1);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getValue(final String s, final Class<T> aClass)
            throws NoSuchElementException, IllegalArgumentException {
        if (aClass == String.class) {
            return (T) getValue(s);
        }
        if (aClass == Boolean.class) {
            return (T) Boolean.valueOf(getValue(s));
        }
        throw new IllegalStateException("Unsupported type: " + aClass.getName());
    }

    @Override
    public <T> T getValue(final String s, final Class<T> aClass, final T t) throws IllegalArgumentException {
        return Optional.ofNullable(getValue(s, aClass)).orElse(t);
    }

    @Override
    public List<String> getValues(final String s) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public List<String> getValues(final String s, final List<String> list) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public <T> List<T> getValues(final String s, final Class<T> aClass)
            throws NoSuchElementException, IllegalArgumentException {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public <T> List<T> getValues(final String s, final Class<T> aClass, final List<T> list)
            throws IllegalArgumentException {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Set<String> getValueSet(final String s) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Set<String> getValueSet(final String s, final Set<String> set) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public <T> Set<T> getValueSet(final String s, final Class<T> aClass)
            throws NoSuchElementException, IllegalArgumentException {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public <T> Set<T> getValueSet(final String s, final Class<T> aClass, final Set<T> set)
            throws IllegalArgumentException {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public <T extends Record> T getConfigData(Class<T> aClass) {
        throw new IllegalStateException("Not supported");
    }

    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        throw new IllegalStateException("Not supported");
    }
}
