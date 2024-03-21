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

package com.swirlds.config.api;

import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.api.source.ConfigSource;
import com.swirlds.config.api.validation.ConfigValidator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * The {@link ConfigurationExtension} is used to extend the configuration api with additional configuration types,
 * converters, validators and sources. This is useful for plugins or modules that want to add their own configuration
 * types to the configuration api.
 *
 * <p>
 * The extension will be discovered by the configuration api using SPI (Service Provider Interface) and must be
 * registered in the {@code module-info.java} file of the module that contains the extension.
 */
public interface ConfigurationExtension {

    /**
     * Returns a collection of configuration data types that should be added to the configuration api. The returned
     * collection must not be null.
     *
     * @return a collection of configuration data types
     */
    @NonNull
    default Collection<Class<? extends Record>> getConfigDataTypes() {
        return Collections.emptySet();
    }

    /**
     * Returns a collection of configuration converters that should be added to the configuration api. The returned
     * collection must not be null.
     *
     * @return a collection of configuration converters
     */
    @NonNull
    default Collection<ConverterPair<?>> getConverters() {
        return Collections.emptySet();
    }

    /**
     * Returns a collection of configuration validators that should be added to the configuration api. The returned
     * collection must not be null.
     *
     * @return a collection of configuration validators
     */
    @NonNull
    default Collection<ConfigValidator> getValidators() {
        return Collections.emptySet();
    }

    /**
     * Returns a collection of configuration sources that should be added to the configuration api. The returned
     * collection must not be null.
     *
     * @return a collection of configuration sources
     */
    @NonNull
    default Collection<ConfigSource> getConfigSources() {
        return Collections.emptySet();
    }

    record ConverterPair<T>(@NonNull Class<T> type, @NonNull ConfigConverter<T> converter) {
        public static <T> ConverterPair<T> of(Class<T> type, ConfigConverter<T> converter) {
            return new ConverterPair<>(type, converter);
        }
    }
}
