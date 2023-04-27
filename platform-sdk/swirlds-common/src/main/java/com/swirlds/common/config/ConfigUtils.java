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

package com.swirlds.common.config;

import com.swirlds.common.constructable.URLClassLoaderWithLookup;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility methods for working with configuration.
 */
public final class ConfigUtils {

    private static final String SWIRLDS_PACKAGE = "com.swirlds";
    private static final String HEDERA_PACKAGE = "com.hedera";

    private ConfigUtils() {}

    /**
     * Scan all classes in classpath/modulepath and register all configuration data types with a configuration builder.
     * This call will only scan the {@code com.swirlds} and {@code com.hedera} packages. If you want to scan all
     * available packages you need to call {@link #scanAndRegisterAllConfigTypes(ConfigurationBuilder, Set)} with an
     * empty string Set.
     *
     * @param configurationBuilder a configuration builder
     * @return the configuration builder that was passed as a param (for fluent api)
     */
    @NonNull
    public static ConfigurationBuilder scanAndRegisterAllConfigTypes(
            @NonNull final ConfigurationBuilder configurationBuilder) {
        return scanAndRegisterAllConfigTypes(configurationBuilder, Set.of(SWIRLDS_PACKAGE, HEDERA_PACKAGE));
    }

    /**
     * Scan all classes in a classpath and register all configuration data types with a configuration builder. If the
     * given {@code packagePrefixes} array is empty all packages will be scanned.
     *
     * @param configurationBuilder a configuration builder
     * @param packagePrefixes      the package prefixes to scan
     * @return the configuration builder that was passed as a param (for fluent api)
     */
    @NonNull
    public static ConfigurationBuilder scanAndRegisterAllConfigTypes(
            @NonNull final ConfigurationBuilder configurationBuilder, @NonNull final Set<String> packagePrefixes) {
        Objects.requireNonNull(packagePrefixes, "packagePrefixes must not be null");
        return scanAndRegisterAllConfigTypes(configurationBuilder, packagePrefixes, Collections.emptyList());
    }

    /**
     * Scan all classes in a classpath and register all configuration data types with a configuration builder.
     *
     * @param configurationBuilder   a configuration builder
     * @param packagePrefixes        the package prefixes to scan
     * @param additionalClassLoaders additional classloaders to scan
     * @return the configuration builder that was passed as a param (for fluent api)
     */
    @NonNull
    public static ConfigurationBuilder scanAndRegisterAllConfigTypes(
            @NonNull final ConfigurationBuilder configurationBuilder,
            @NonNull final Set<String> packagePrefixes,
            @NonNull final List<URLClassLoaderWithLookup> additionalClassLoaders) {
        Objects.requireNonNull(configurationBuilder, "configurationBuilder must not be null");
        loadAllConfigDataRecords(packagePrefixes, additionalClassLoaders)
                .forEach(configurationBuilder::withConfigDataType);
        return configurationBuilder;
    }

    @NonNull
    private static Set<Class<? extends Record>> loadAllConfigDataRecords(
            @NonNull final Set<String> packagePrefixes,
            @NonNull final List<URLClassLoaderWithLookup> additionalClassLoaders) {
        Objects.requireNonNull(packagePrefixes, "packagePrefix must not be null");
        Objects.requireNonNull(additionalClassLoaders, "additionalClassLoaders must not be null");

        final ClassGraph classGraph = new ClassGraph().enableAnnotationInfo();

        if (!packagePrefixes.isEmpty()) {
            classGraph.whitelistPackages(packagePrefixes.toArray(new String[0]));
        }

        for (final URLClassLoaderWithLookup classloader : additionalClassLoaders) {
            classGraph.addClassLoader(classloader);
        }

        try (final ScanResult result = classGraph.scan()) {
            final ClassInfoList classInfos = result.getClassesWithAnnotation(ConfigData.class.getName());
            return classInfos.stream()
                    .map(classInfo -> classInfo.loadClass())
                    .filter(clazz -> clazz.isRecord())
                    .map(clazz -> (Class<? extends Record>) clazz)
                    .collect(Collectors.toSet());
        }
    }
}
