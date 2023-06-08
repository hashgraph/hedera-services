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
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassGraphException;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.extension.ExtendWith;


/** Uses the classgraph library to search for `HapiSuite`s and other interesting things relevant to `SuitesInspector */
public class SuiteSearcher {

    /* Search repo (i.e., jars) looking for all concrete subclasses of `HapiSuite` */
    public Pair<List<Class<?>>, List<String>> getAllHapiSuiteConcreteSubclasses() {
        return getFilteredHapiSuiteSubclasses(ci -> !ci.isAbstract());
    }

    /* Search repo (i.e., jars) looking for all abstract subclasses of `HapiSuite` */
    public Pair<List<Class<?>>, List<String>> getAllHapiSuiteAbstractSubclasses() {
        final var subclasses = getFilteredHapiSuiteSubclasses(ClassInfo::isAbstract);
        final var hapiSuiteSubclasses = new ArrayList<Class<?>>(1 + subclasses.getLeft().size());
        hapiSuiteSubclasses.add(HapiSuite.class);
        hapiSuiteSubclasses.addAll(subclasses.getLeft());
        return Pair.of(hapiSuiteSubclasses, subclasses.getRight());
    }

     Pair<List<Class<?>>, List<String>> getFilteredHapiSuiteSubclasses(@NonNull final ClassInfoList.ClassInfoFilter filter) {
        try (final var scanResult = new ClassGraph()
                .whitelistJars(SuiteRepoParameters.hapiSuiteContainingJars
                )
                .whitelistPackages(SuiteRepoParameters.hapiSuiteRootPackageName)
                .scan()) {
            return Pair.of(
                    scanResult
                            .getSubclasses(HapiSuite.class.getName())
                            .filter(filter)
                            .loadClasses(),
                    List.of());
        } catch (final ClassGraphException ex) {
            return Pair.of(
                    List.of(), List.of("*** exception getting classgraph then getting classes: %s%n".formatted(ex)));
        }
    }
}
