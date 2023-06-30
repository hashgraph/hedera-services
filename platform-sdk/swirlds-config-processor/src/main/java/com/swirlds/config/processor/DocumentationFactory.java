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
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

public class DocumentationFactory {

    private DocumentationFactory() {}

    public static void doWork(
            @NonNull final ConfigDataRecordDefinition configDataRecordDefinition,
            @NonNull final Path configDocumentationFile)
            throws IOException {
        Objects.requireNonNull(configDataRecordDefinition, "configDataRecordDefinition must not be null");
        Objects.requireNonNull(configDocumentationFile, "configDocumentationFile must not be null");

        try (FileWriter writer = new FileWriter(configDocumentationFile.toString(), true)) {
            configDataRecordDefinition.propertyDefinitions().forEach(propertyDefinition -> {
                try {
                    writer.write("## " + propertyDefinition.name() + "\n\n");
                    writer.write("**type:** " + propertyDefinition.type() + "\n\n");
                    writer.write("**default value:** " + propertyDefinition.defaultValue() + "\n\n");
                    writer.write("**description:** " + propertyDefinition.description() + "\n\n");
                    writer.write("\n");
                } catch (IOException e) {
                    throw new RuntimeException("Error while writing doc", e);
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error while writing doc", e);
        }
    }
}
