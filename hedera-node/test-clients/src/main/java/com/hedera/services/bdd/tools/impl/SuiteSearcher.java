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
import com.hedera.services.bdd.suites.tools.annotation.BddPrerequisiteSpec;
import com.swirlds.common.AutoCloseableNonThrowing;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.AnnotationEnumValue;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ClassInfoList;
import io.github.classgraph.ScanResult;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Uses the classgraph library to search for `HapiSuite`s and other interesting things relevant to `SuitesInspector */
public class SuiteSearcher implements AutoCloseableNonThrowing {

    Optional<ScanResult> scanResult = Optional.empty();

    /* Caller do the scan before attempting to get any information */
    public void doScan() {
        if (scanResult.isEmpty())
            scanResult = Optional.of(new ClassGraph()
                    .whitelistJars(SuiteRepoParameters.hapiSuiteContainingJars)
                    .whitelistPackages(SuiteRepoParameters.hapiSuiteRootPackageName)
                    .enableClassInfo()
                    .ignoreClassVisibility()
                    .enableMethodInfo()
                    .ignoreMethodVisibility()
                    .enableAnnotationInfo()
                    .disableRuntimeInvisibleAnnotations()
                    .enableInterClassDependencies()
                    .initializeLoadedClasses()
                    .scan()); // can throw ClassGraphException (derived from IllegalArgumentException)
    }

    @Override
    public void close() {
        scanResult.ifPresent(ScanResult::close);
        scanResult = Optional.empty();
    }

    @NonNull
    Optional<ScanResult> getScan() {
        doScan();
        return scanResult;
    }

    Supplier<IllegalStateException> scanInvalid =
            () -> new IllegalStateException("Classgraph scan invalid (must have thrown an exception when scanning)");

    /* Search repo (i.e., jars) looking for all concrete subclasses of `HapiSuite` */
    @NonNull
    public List<Class<?>> getAllHapiSuiteConcreteSubclasses() {
        doScan();
        return getFilteredHapiSuiteSubclasses(ci -> !ci.isAbstract());
    }

    /* Search repo (i.e., jars) looking for all abstract subclasses of `HapiSuite` */
    @NonNull
    public List<Class<?>> getAllHapiSuiteAbstractSubclasses() {
        doScan();
        final var subclasses = getFilteredHapiSuiteSubclasses(ClassInfo::isAbstract);
        final var hapiSuiteSubclasses = new ArrayList<Class<?>>(1 + subclasses.size());
        hapiSuiteSubclasses.add(HapiSuite.class);
        hapiSuiteSubclasses.addAll(subclasses);
        return hapiSuiteSubclasses;
    }

    @NonNull
    List<Class<?>> getFilteredHapiSuiteSubclasses(@NonNull final ClassInfoList.ClassInfoFilter filter) {
        return getScan()
                .orElseThrow(scanInvalid)
                .getSubclasses(HapiSuite.class.getName())
                .filter(filter)
                .loadClasses();
    }

    public record AnnotatedMethod(
            @NonNull String annotationSimpleName,
            @NonNull String klassName,
            @NonNull String methodSimpleName,
            @NonNull Optional<BddPrerequisiteSpec.Scope> scope) {}

    /** find all methods with a given annotation */
    @NonNull
    public List<AnnotatedMethod> getAllBddAnnotatedMethods(@NonNull final Class<? extends Annotation> annotation) {
        final var klassesOfInterest =
                getScan().orElseThrow(scanInvalid).getClassesWithMethodAnnotation(annotation.getName());
        final var methodsOfInterest = klassesOfInterest.stream()
                .flatMap(ci -> ci.getDeclaredMethodInfo().stream())
                .filter(mi -> mi.hasAnnotation(annotation.getName()))
                .toList();
        return methodsOfInterest.stream()
                .map(mi -> {
                    final var klassName = mi.getClassInfo().getName();
                    final var methodName = getSimpleName(mi.getName());
                    Optional<BddPrerequisiteSpec.Scope> scope = Optional.empty();

                    final var annotationInfo = mi.getAnnotationInfo(annotation.getName());
                    final var annotationParameters = annotationInfo.getParameterValues();
                    if (!annotationParameters.isEmpty()) {
                        final var annotationParam = annotationParameters.get(0);
                        if (annotationParam.getValue() instanceof AnnotationEnumValue scopeParam) {
                            final var scopeValue = scopeParam.loadClassAndReturnEnumValue();
                            scope = Optional.ofNullable((BddPrerequisiteSpec.Scope) scopeValue);
                        }
                    }

                    return new AnnotatedMethod(annotation.getSimpleName(), klassName, methodName, scope);
                })
                .distinct()
                .toList();
    }

    @NonNull
    String getSimpleName(@NonNull final String name) {
        final var pieces = name.split("[.]");
        return pieces[pieces.length - 1];
    }
}
