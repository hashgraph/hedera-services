/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.config;

import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlatformConfigurationExtensionTests {

    /**
     * A simple config builder implementation. Mockito doesn't play nicely with methods that expect Class parameters.
     */
    private static class DummyConfigBuilder implements ConfigurationBuilder {

        private final List<Class<?>> configDataTypes = new ArrayList<>();

        @NonNull
        public List<Class<?>> getConfigDataTypes() {
            return configDataTypes;
        }

        @NonNull
        @Override
        public ConfigurationBuilder withSource(@NonNull ConfigSource configSource) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ConfigurationBuilder withSources(@NonNull ConfigSource... configSources) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ConfigurationBuilder withConverter(@NonNull ConfigConverter<?> converter) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public <T> ConfigurationBuilder withConverter(
                @NonNull Class<T> converterType, @NonNull ConfigConverter<T> converter) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ConfigurationBuilder withConverters(@NonNull ConfigConverter<?>... converters)
                throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ConfigurationBuilder withValidator(@NonNull ConfigValidator validator) throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public ConfigurationBuilder withValidators(@NonNull ConfigValidator... validators)
                throws IllegalStateException {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public <T extends Record> ConfigurationBuilder withConfigDataType(@NonNull Class<T> type)
                throws IllegalStateException {
            configDataTypes.add(type);
            return this;
        }

        @NonNull
        @Override
        public ConfigurationBuilder withConfigDataTypes(@NonNull Class<? extends Record>... types)
                throws IllegalStateException {
            configDataTypes.addAll(List.of(types));
            return this;
        }

        @NonNull
        @Override
        public ConfigurationBuilder withValue(@NonNull String propertyName, @NonNull String value) {
            throw new UnsupportedOperationException();
        }

        @NonNull
        @Override
        public Configuration build() {
            throw new UnsupportedOperationException();
        }
    }

    @Test
    void requireAlphabetizedConfigRegistration() {
        final DummyConfigBuilder configBuilder = new DummyConfigBuilder();
        new PlatformConfigurationExtension().extendConfiguration(configBuilder);

        String previous = "";
        for (final Class<?> configDataType : configBuilder.getConfigDataTypes()) {
            final String current = configDataType.getSimpleName();
            if (current.compareTo(previous) < 0) {
                throw new IllegalStateException(
                        "Config data types are not alphabetized: " + previous + " should come after " + current);
            }
            previous = current;
        }
    }
}
