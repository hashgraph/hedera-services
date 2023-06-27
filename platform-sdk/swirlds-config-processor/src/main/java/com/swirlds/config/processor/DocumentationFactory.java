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

package com.swirlds.config.processor;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;

public class DocumentationFactory {

    public static void doWork(@NonNull final ConfigDataRecordDefinition configDataRecordDefinition) throws IOException {
        Objects.requireNonNull(configDataRecordDefinition, "configDataRecordDefinition must not be null");

        System.out.println("Result for config data record '" + configDataRecordDefinition.simpleClassName() + "':");
        configDataRecordDefinition.propertyDefinitions().forEach(propertyDefinition -> {
            System.out.println("  " + propertyDefinition.name() + " (" + propertyDefinition.type() + "):");
            System.out.println("    default value: " + propertyDefinition.defaultValue());
            System.out.println("    description: " + propertyDefinition.description());
        });
    }
}
