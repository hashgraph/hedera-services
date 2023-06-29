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

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.FileObject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.JavaRecord;
import org.jboss.forge.roaster.model.JavaRecordComponent;
import org.jboss.forge.roaster.model.JavaType;

public class ConfigRecordParser {

    private ConfigRecordParser() {}

    @NonNull
    private static Map<String, String> getJavaDocParams(@NonNull final JavaType<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        final Map<String, String> paramDoc = new HashMap<>();
        type.getJavaDoc().getTags(ConfigProcessorConstants.JAVADOC_PARAM).forEach(tag -> {
            int split = tag.getValue().indexOf(" ");
            paramDoc.put(
                    tag.getValue().substring(0, split).trim(),
                    tag.getValue().substring(split + 1).trim());
        });
        return Collections.unmodifiableMap(paramDoc);
    }

    @NonNull
    public static ConfigDataRecordDefinition parse(@NonNull final FileObject javaSourceFile) throws IOException {
        try (final InputStream inputStream = javaSourceFile.openInputStream()) {
            final JavaType<?> type = Roaster.parse(inputStream);
            final Annotation<?> annotation = type.getAnnotation(ConfigData.class);
            final String configDataValue = annotation.getStringValue(ConfigProcessorConstants.VALUE_FIELD_NAME);
            final Map<String, String> paramDoc = getJavaDocParams(type);
            if (type instanceof JavaRecord<?> record) {
                final Set<ConfigDataPropertyDefinition> propertyDefinitions = record.getRecordComponents().stream()
                        .map(javaRecordComponent ->
                                getConfigDataPropertyDefinition(configDataValue, paramDoc, javaRecordComponent))
                        .collect(Collectors.toSet());
                final String qualifiedName = type.getQualifiedName().toString();
                final int split = qualifiedName.lastIndexOf(".");
                return new ConfigDataRecordDefinition(
                        type.getPackage(), qualifiedName.substring(split + 1), configDataValue, propertyDefinitions);
            } else {
                throw new IllegalArgumentException(
                        "ConfigData annotation must be on a record! Type " + type + " is not a record!");
            }
        }
    }

    @Nullable
    private static String getDefaultValue(@NonNull final JavaRecordComponent<?> javaRecordComponent) {
        final Annotation<?> configPropertyAnnotation = javaRecordComponent.getAnnotation(ConfigProperty.class);
        if (configPropertyAnnotation != null) {
            final String annotationDefaultValue =
                    configPropertyAnnotation.getStringValue(ConfigProcessorConstants.DEFAULT_VALUE_FIELD_NAME);
            if (annotationDefaultValue != null
                    && !Objects.equals(annotationDefaultValue, ConfigProperty.NULL_DEFAULT_VALUE)) {
                return annotationDefaultValue;
            }
        }
        return null;
    }

    @NonNull
    private static String getName(
            @Nullable final String configDataValue, @NonNull final JavaRecordComponent javaRecordComponent) {
        String name = javaRecordComponent.getName();
        final Annotation<?> configPropertyAnnotation = javaRecordComponent.getAnnotation(ConfigProperty.class);
        if (configPropertyAnnotation != null) {
            final String annotationValue =
                    configPropertyAnnotation.getStringValue(ConfigProcessorConstants.VALUE_FIELD_NAME);
            if (annotationValue != null) {
                name = annotationValue;
            }
        }
        if (configDataValue != null) {
            return configDataValue + "." + name;
        }
        return name;
    }

    @NonNull
    private static ConfigDataPropertyDefinition getConfigDataPropertyDefinition(
            @Nullable final String configDataValue,
            final @NonNull Map<String, String> paramDoc,
            @NonNull final JavaRecordComponent<?> javaRecordComponent) {
        Objects.requireNonNull(paramDoc, "paramDoc must not be null");
        Objects.requireNonNull(javaRecordComponent, "javaRecordComponent must not be null");

        final String propertyName = getName(configDataValue, javaRecordComponent);
        final String propertyDefaultValue = getDefaultValue(javaRecordComponent);
        final String propertyDescription = paramDoc.get(javaRecordComponent.getName());
        final String propertyType = javaRecordComponent.getType().getQualifiedName();

        return new ConfigDataPropertyDefinition(
                javaRecordComponent.getName(), propertyName, propertyType, propertyDefaultValue, propertyDescription);
    }
}
