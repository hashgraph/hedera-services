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

package com.hedera.services.bdd.tools.impl;

import com.hedera.services.bdd.suites.HapiSuite;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/** Uses the classgraph library to search for `HapiSuite`s */
public class SuiteSearcher {

    public Pair<List<Class<?>>, List<String>> getAllHapiSuiteSubclasses() {
        try (final var scanResult = new ClassGraph()
                .whitelistJars("hedera-*.jar", "hapi-*.jar", "test-*.jar", "cli-*.jar")
                .whitelistPackages("com.hedera.services.bdd")
                .scan()) {
            return Pair.of(scanResult.getSubclasses(HapiSuite.class.getName())
                    .filter(ci -> !ci.isAbstract())
                    .loadClasses(), List.of());
        } catch (final ClassGraphException ex) {
            return Pair.of(
                    List.of(), List.of("*** exception getting classgraph then getting classes: %s%n".formatted(ex)));
        }
    }
}
