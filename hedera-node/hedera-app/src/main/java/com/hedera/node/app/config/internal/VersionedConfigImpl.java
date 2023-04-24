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

package com.hedera.node.app.config.internal;

import com.hedera.node.app.spi.config.VersionedConfiguration;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Implementation of {@link VersionedConfiguration} that wraps a {@link Configuration} and a version.
 */
public class VersionedConfigImpl implements VersionedConfiguration {

    private final Configuration wrappedConfig;

    private final long version;

    public VersionedConfigImpl(@NonNull final Configuration wrappedConfig, final long version) {
        this.wrappedConfig = Objects.requireNonNull(wrappedConfig, "wrappedConfig must not be null");
        this.version = version;
    }

    @Override
    public long getVersion() {
        return version;
    }

    @Override
    public Stream<String> getPropertyNames() {
        return wrappedConfig.getPropertyNames();
    }

    @Override
    public boolean exists(final String s) {
        return wrappedConfig.exists(s);
    }

    @Override
    public String getValue(final String s) throws NoSuchElementException {
        return wrappedConfig.getValue(s);
    }

    @Override
    public String getValue(final String s, final String s1) {
        return wrappedConfig.getValue(s, s1);
    }

    @Override
    public <T> T getValue(final String s, final Class<T> aClass)
            throws NoSuchElementException, IllegalArgumentException {
        return wrappedConfig.getValue(s, aClass);
    }

    @Override
    public <T> T getValue(final String s, final Class<T> aClass, final T t) throws IllegalArgumentException {
        return wrappedConfig.getValue(s, aClass, t);
    }

    @Override
    public List<String> getValues(final String s) {
        return wrappedConfig.getValues(s);
    }

    @Override
    public List<String> getValues(final String s, final List<String> list) {
        return wrappedConfig.getValues(s, list);
    }

    @Override
    public <T> List<T> getValues(final String s, final Class<T> aClass)
            throws NoSuchElementException, IllegalArgumentException {
        return wrappedConfig.getValues(s, aClass);
    }

    @Override
    public <T> List<T> getValues(final String s, final Class<T> aClass, final List<T> list)
            throws IllegalArgumentException {
        return wrappedConfig.getValues(s, aClass, list);
    }

    @Override
    public <T extends Record> T getConfigData(final Class<T> aClass) {
        return wrappedConfig.getConfigData(aClass);
    }

    @Override
    public Collection<Class<? extends Record>> getConfigDataTypes() {
        return wrappedConfig.getConfigDataTypes();
    }
}
