/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.tasks;

import static org.assertj.core.api.Assertions.assertThat;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.MethodInfo;
import io.github.classgraph.MethodParameterInfo;
import io.github.classgraph.TypeSignature;
import java.lang.reflect.Method;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(TaskModuleTest.AllSystemTasksAreWiredUpNameGenerator.class)
class TaskModuleTest {

    /** This array must contain the names of _all_ `SystemTask`s */
    static final String[] EXPECTED_SYSTEM_TASK_SIMPLE_NAMES = {"ExpiryProcess"};

    /** Scope for all modules containing `SystemTasks` (to limit the search for them) */
    static final String MODULE_PREFIX_FOR_ALL_SYSTEM_TASKS = "com.hedera.node.app.service.*";

    /** Validate that all {@link com.hedera.node.app.service.mono.state.tasks.SystemTask} subclasses
     * are properly wired up into the Dagger map that provides them to the {@link SystemTaskManager}. (Because it would be easy to forget.)
     */
    @Test
    void validateThatAllSystemTasksAreWiredUp() {

        try (var scanResult = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .enableAnnotationInfo()
                .disableRuntimeInvisibleAnnotations()
                .whitelistModules(MODULE_PREFIX_FOR_ALL_SYSTEM_TASKS)
                .scan()) {

            final var systemTaskSimpleNames = scanResult
                    .getClassesImplementing("com.hedera.node.app.service.mono.state.tasks.SystemTask")
                    .getNames()
                    .stream()
                    .map(this::trimToSimpleName)
                    .toList();
            assertThat(systemTaskSimpleNames).containsExactlyInAnyOrder(EXPECTED_SYSTEM_TASK_SIMPLE_NAMES);

            final var taskModuleBindingMethodsParameterTypeNames = scanResult
                    .getClassInfo("com.hedera.node.app.service.mono.state.tasks.TaskModule")
                    .getMethodInfo()
                    .filter(m -> m.hasAnnotation("dagger.Binds") && m.hasAnnotation("dagger.multibindings.IntoMap"))
                    .stream()
                    .map(MethodInfo::getParameterInfo)
                    .map(mpis -> mpis[0])
                    .map(MethodParameterInfo::getTypeDescriptor)
                    .map(TypeSignature::toStringWithSimpleNames)
                    .toList();
            assertThat(taskModuleBindingMethodsParameterTypeNames)
                    .containsExactlyInAnyOrder(EXPECTED_SYSTEM_TASK_SIMPLE_NAMES);
        }
    }

    @NonNull
    String trimToSimpleName(@NonNull final String name) {
        return name.substring(name.lastIndexOf('.') + 1);
    }

    static class AllSystemTasksAreWiredUpNameGenerator implements DisplayNameGenerator {

        @Override
        public String generateDisplayNameForMethod(final Class<?> testClass, final Method testMethod) {
            return "%s.%s for %s"
                    .formatted(
                            testClass.getSimpleName(),
                            testMethod.getName(),
                            String.join(", ", EXPECTED_SYSTEM_TASK_SIMPLE_NAMES));
        }

        @Override
        public String generateDisplayNameForClass(final Class<?> testClass) {
            return testClass.getSimpleName();
        }

        @Override
        public String generateDisplayNameForNestedClass(final Class<?> nestedClass) {
            throw new UnsupportedOperationException();
        }
    }
}
