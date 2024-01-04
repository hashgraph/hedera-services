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

package com.swirlds.config.processor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Metadata of a config data record (a Java Record that is annotated by @ConfigData).
 *
 * @param packageName         the package name like "com.swirlds.config.foo"
 * @param simpleClassName     the simple class name like "FooConfig"
 * @param configDataName      the prefix that is used for config properties that are part of the config data like
 *                            "state" for properties that start with "state." like "state.maxSize".
 * @param propertyDefinitions the property definitions
 */
public record ConfigDataRecordDefinition(
        @NonNull String packageName,
        @NonNull String simpleClassName,
        @NonNull String configDataName,
        @NonNull Set<ConfigDataPropertyDefinition> propertyDefinitions) {}
