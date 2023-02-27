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
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility methods for working with configuration.
 */
public final class ConfigUtils {

    private ConfigUtils() {}

    /**
     * This method will add all config data records (see {@link ConfigData}) that are on the classpath to the given
     * builder
     *
     * @param configurationBuilder
     * 		the builder
     * @return the builder (for fluent api usage)
     */
    public static ConfigurationBuilder addAllConfigDataOnClasspath(final ConfigurationBuilder configurationBuilder) {
        try (ScanResult result = new ClassGraph().enableAnnotationInfo().scan()) {
            final Set<? extends Class<? extends Record>> configDataRecordTypes =
                    result.getClassesWithAnnotation(ConfigData.class.getName()).stream()
                            .map(classInfo -> (Class<? extends Record>) classInfo.loadClass())
                            .collect(Collectors.toSet());
            configDataRecordTypes.forEach(type -> configurationBuilder.withConfigDataType(type));
            return configurationBuilder;
        }
    }

    /**
     * Scan all classes in a classpath and register all configuration data types with a configuration builder.
     *
     * @param configurationBuilder
     * 		a configuration builder
     * @param packagePrefix
     * 		the package prefix to scan
     * @param additionalClassLoaders
     * 		additional classloaders to scan
     */
    @SuppressWarnings("unchecked")
    public static void scanAndRegisterAllConfigTypes(
            final ConfigurationBuilder configurationBuilder,
            final String packagePrefix,
            final URLClassLoaderWithLookup... additionalClassLoaders) {

        final ClassGraph classGraph = new ClassGraph().enableAnnotationInfo().whitelistPackages(packagePrefix);

        if (additionalClassLoaders != null) {
            for (final URLClassLoaderWithLookup classloader : additionalClassLoaders) {
                classGraph.addClassLoader(classloader);
            }
        }

        try (final ScanResult result = classGraph.scan()) {
            final ClassInfoList classInfos = result.getClassesWithAnnotation(ConfigData.class.getName());
            classInfos.stream()
                    .map(classInfo -> (Class<? extends Record>) classInfo.loadClass())
                    .forEach(configurationBuilder::withConfigDataType);
        }
    }

    /**
     * Returns the user directory path specified by the {@code user.dir} system property.
     *
     * @return the user directory path
     */
    public static String getUserDir() {
        return System.getProperty("user.dir");
    }
}
