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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class ReflectionUtils {

    @NonNull
    Result<Set<Class<?>>> getClassesFromNames(@NonNull final Collection<String> names) {
        final var klasses = new HashSet<Class<?>>(names.size());
        final var aggex = new AggregateException();
        for (@NonNull final var name : names) {
            getClassFromName(name).onOk(klasses::add).onErr(aggex::add);
        }
        return aggex.isEmpty() ? Result.ok(klasses) : Result.err(aggex);
    }

    @NonNull
    Result<Class<?>> getClassFromName(@NonNull final String name) {
        try {
            return Result.ok(Class.forName(name));
        } catch (final ClassNotFoundException ex) {
            return Result.err(ex);
        }
    }

    @NonNull
    Result<Set<Class<?>>> getClassesFromSimpleNames(
            @NonNull final Collection<String> names,
            @NonNull final String rootPackageName,
            @NonNull final Collection<String> packageNames) {
        final var klasses = new HashSet<Class<?>>(names.size());
        final var aggex = new AggregateException();
        for (@NonNull final var name : names) {
            getClassFromSimpleName(name, rootPackageName, packageNames)
                    .onOk(klasses::add)
                    .onErr(aggex::add);
        }
        return aggex.isEmpty() ? Result.ok(klasses) : Result.err(aggex);
    }

    @NonNull
    Result<Class<?>> getClassFromSimpleName(
            @NonNull final String classSimpleName,
            @NonNull final String rootPackageName,
            @NonNull final Collection<String> packageNames) {
        for (final var packageName : packageNames) {
            try {
                return Result.ok(Class.forName(packageName + '.' + classSimpleName));
            } catch (final ClassNotFoundException ＿) {
                ;
            }
            try {
                return Result.ok(Class.forName(packageName + '.' + classSimpleName + "Suite"));
            } catch (final ClassNotFoundException ＿) {
                ;
            }
        }
        return Result.err(new ClassNotFoundException(
                "%s not found in packages under %s".formatted(classSimpleName, rootPackageName)));
    }

    @NonNull
    Collection<String> getPackagesUnder(@NonNull final String rootName) {
        return Arrays.stream(Package.getPackages())
                .map(Package::getName)
                .filter(s -> s.startsWith(rootName))
                .collect(Collectors.toSet());
    }
}
