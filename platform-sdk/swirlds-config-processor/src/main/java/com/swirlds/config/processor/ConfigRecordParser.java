/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.config.processor;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
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

    public static final String JAVADOC_PARAM = "@param";
    public static final String VALUE_FIELD_NAME = "value";
    public static final String DEFAULT_VALUE_FIELD_NAME = "defaultValue";

    private static Map<String, String> getJavaDocParams(JavaType<?> type) {
        Map<String, String> paramDoc = new HashMap<>();
        type.getJavaDoc().getTags(JAVADOC_PARAM).forEach(tag -> {
            paramDoc.put(tag.getName(), tag.getValue());
        });
        return Collections.unmodifiableMap(paramDoc);
    }

    public static ConfigDataRecordDefinition parse(final FileObject javaSourceFile) throws IOException {
        try (InputStream inputStream = javaSourceFile.openInputStream()) {
            JavaType<?> type = Roaster.parse(inputStream);
            Annotation<?> annotation = type.getAnnotation(ConfigData.class);
            String configDataValue = annotation.getStringValue(VALUE_FIELD_NAME);

            Map<String, String> paramDoc = getJavaDocParams(type);

            JavaRecord<?> record = (JavaRecord<?>) type;
            Set<ConfigDataPropertyDefinition> propertyDefinitions = record.getRecordComponents().stream()
                    .map(javaRecordComponent -> getConfigDataPropertyDefinition(configDataValue, paramDoc,
                            javaRecordComponent))
                    .collect(Collectors.toSet());

            return new ConfigDataRecordDefinition(
                    type.getQualifiedName(),
                    configDataValue,
                    propertyDefinitions
            );
        }
    }

    private static String getDefaultValue(JavaRecordComponent javaRecordComponent) {
        Annotation configPropertyAnnotation = javaRecordComponent.getAnnotation(ConfigProperty.class);
        if (configPropertyAnnotation != null) {
            String annotationDefaultValue = configPropertyAnnotation.getStringValue(
                    DEFAULT_VALUE_FIELD_NAME);
            if (annotationDefaultValue != null && !Objects.equals(annotationDefaultValue,
                    ConfigProperty.NULL_DEFAULT_VALUE)) {
                return annotationDefaultValue;
            }
        }
        return null;
    }

    private static String getName(String configDataValue, JavaRecordComponent javaRecordComponent) {
        String name = javaRecordComponent.getName();
        Annotation configPropertyAnnotation = javaRecordComponent.getAnnotation(ConfigProperty.class);
        if (configPropertyAnnotation != null) {
            String annotationValue = configPropertyAnnotation.getStringValue(VALUE_FIELD_NAME);
            if (annotationValue != null) {
                name = annotationValue;
            }
        }
        String propertyName = name;
        if (configDataValue != null) {
            propertyName = configDataValue + "." + name;
        }
        return propertyName;
    }
    
    private static ConfigDataPropertyDefinition getConfigDataPropertyDefinition(String configDataValue,
            Map<String, String> paramDoc, JavaRecordComponent javaRecordComponent) {
        final String propertyName = getName(configDataValue, javaRecordComponent);
        final String propertyDefaultValue = getDefaultValue(javaRecordComponent);
        final String propertyDescription = paramDoc.get(javaRecordComponent.getName());
        final String propertyType = javaRecordComponent.getType().getQualifiedName();

        return new ConfigDataPropertyDefinition(
                propertyName,
                propertyType,
                propertyDefaultValue,
                propertyDescription
        );
    }
}
