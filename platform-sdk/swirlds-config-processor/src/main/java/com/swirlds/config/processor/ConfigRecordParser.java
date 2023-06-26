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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.tools.FileObject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.Annotation;
import org.jboss.forge.roaster.model.JavaRecord;
import org.jboss.forge.roaster.model.JavaType;

public class ConfigRecordParser {

    public static ConfigDataRecordDefinition parse(final FileObject javaSourceFile) throws IOException {
        JavaType<?> type = Roaster.parse(javaSourceFile.openInputStream());

        Annotation<?> annotation = type.getAnnotation(ConfigData.class);
        String configDataValue = annotation.getStringValue("value");

        Map<String, String> paramDoc = new HashMap<>();
        type.getJavaDoc().getTags("@param").forEach(tag -> {
            paramDoc.put(tag.getName(), tag.getValue());
        });

        JavaRecord<?> record = (JavaRecord<?>) type;
        Set<ConfigDataPropertyDefinition> propertyDefinitions = record.getRecordComponents().stream()
                .map(javaRecordComponent -> {
                    String name = javaRecordComponent.getName();
                    String propertyDefaultValue = null;
                    String propertyDescription = paramDoc.get(name);
                    Annotation configPropertyAnnotation = javaRecordComponent.getAnnotation(ConfigProperty.class);
                    if (configPropertyAnnotation != null) {
                        String annotationValue = configPropertyAnnotation.getStringValue("value");
                        if (annotationValue != null) {
                            name = annotationValue;
                        }
                        String annotationDefaultValue = configPropertyAnnotation.getStringValue("defaultValue");
                        if (annotationDefaultValue != null && !Objects.equals(annotationDefaultValue,
                                ConfigProperty.NULL_DEFAULT_VALUE)) {
                            propertyDefaultValue = annotationDefaultValue;
                        }
                    }
                    String propertyName = name;
                    if (configDataValue != null) {
                        propertyName = configDataValue + "." + name;
                    }
                    String propertyType = javaRecordComponent.getType().getQualifiedName();

                    return new ConfigDataPropertyDefinition(
                            propertyName,
                            propertyType,
                            propertyDefaultValue,
                            propertyDescription
                    );
                }).collect(Collectors.toSet());

        return new ConfigDataRecordDefinition(
                type.getQualifiedName(),
                configDataValue,
                propertyDefinitions
        );

    }
}
