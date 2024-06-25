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

package com.swirlds.config.extensions.test.fixtures;
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

import com.swirlds.config.api.ConfigData;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility methods for working with configuration.
 */
public final class ConfigUtils {

    private ConfigUtils() {}

    /**
     * Scan all classes on the classpath / modulepath that are under the provided {@code packagePrefixes}.
     * If the given {@code packagePrefixes} Set is empty all packages will be scanned.
     *
     * @param packagePrefixes      the package prefixes to scan
     * @return the set of all configuration data types found
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public static Set<Class<? extends Record>> loadAllConfigDataRecords(@NonNull final Set<String> packagePrefixes) {
        Objects.requireNonNull(packagePrefixes, "packagePrefix must not be null");

        final ClassGraph classGraph = new ClassGraph().enableAnnotationInfo();

        if (!packagePrefixes.isEmpty()) {
            classGraph.whitelistPackages(packagePrefixes.toArray(new String[0]));
        }

        try (final ScanResult result = classGraph.scan()) {
            final ClassInfoList classInfos = result.getClassesWithAnnotation(ConfigData.class.getName());
            return classInfos.stream()
                    .map(ClassInfo::loadClass)
                    .filter(Class::isRecord)
                    .map(clazz -> (Class<? extends Record>) clazz)
                    .collect(Collectors.toSet());
        }
    }
}
