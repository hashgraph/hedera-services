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

package com.hedera.services.bdd.suites.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;

public class DynamicTestUtils {

    /**
     * Loops all the suppliers and their methods. If the method is annotated with the annotationClass annotation
     * we invoke the method get the {@code Stream<DynamicTest>} and collect it to a list.
     * @param suppliers Test class suppliers that contain tests methods returning {@code Stream<DynamicTest>}
     * @param ignoredTests The test methods that are ignored
     * @param annotationClass The target annotation
     * @return list of all {@code Stream<DynamicTest>} collected from all found methods
     */
    public static List<Stream<DynamicTest>> extractAllTestAnnotatedMethods(
            @NonNull Supplier<?>[] suppliers,
            @NonNull List<String> ignoredTests,
            @NonNull Class<? extends Annotation> annotationClass) {
        var allDynamicTests = new ArrayList<Stream<DynamicTest>>();
        for (Supplier<?> supplier : suppliers) {
            Object instance = supplier.get();
            for (Method method : instance.getClass().getDeclaredMethods()) {
                if (method.isAnnotationPresent(annotationClass)) {
                    if (ignoredTests.contains(method.getName())) {
                        continue;
                    }
                    method.setAccessible(true);
                    Stream<DynamicTest> testInvokeResult = null;
                    try {
                        testInvokeResult = (Stream<DynamicTest>) method.invoke(instance);
                    } catch (Exception e) {
                        throw new RuntimeException(e); // no handle for now
                    }
                    var dynamicTest = DynamicTest.dynamicTest(
                            method.getName(), testInvokeResult.toList().get(0).getExecutable());
                    allDynamicTests.add(Stream.of(dynamicTest));
                }
            }
        }
        return allDynamicTests;
    }
}
