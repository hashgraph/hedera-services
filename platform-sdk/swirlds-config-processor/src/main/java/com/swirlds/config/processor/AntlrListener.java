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
import com.swirlds.config.processor.generated.JavaParser.AnnotationContext;
import com.swirlds.config.processor.generated.JavaParser.RecordComponentContext;
import com.swirlds.config.processor.generated.JavaParser.RecordDeclarationContext;
import com.swirlds.config.processor.generated.JavaParserBaseListener;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class AntlrListener extends JavaParserBaseListener {

    private ConfigDataRecordDefinition definition;

    private static boolean isAnnotatedWithConfigData(
            final RecordDeclarationContext ctx, String packageName, List<String> imports) {
        final List<AnnotationContext> allAnnotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigData.class, allAnnotations, packageName, imports)
                .isPresent();
    }

    private static Optional<AnnotationContext> getConfigDataAnnotation(
            final RecordDeclarationContext ctx, final String packageName, final List<String> imports) {
        final List<AnnotationContext> annotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigData.class, annotations, packageName, imports);
    }

    private static String getConfigDataAnnotationValue(
            final RecordDeclarationContext ctx, final String packageName, final List<String> imports) {
        return getConfigDataAnnotation(ctx, packageName, imports)
                .map(annotationContext -> annotationContext.elementValue())
                .map(elementValueContext -> elementValueContext.getText())
                .orElse("");
    }

    private static Optional<AnnotationContext> getConfigPropertyAnnotation(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        final List<AnnotationContext> annotations = AntlrUtils.getAllAnnotations(ctx);
        return AntlrUtils.findAnnotationOfType(ConfigData.class, annotations, packageName, imports);
    }

    private static String getConfigPropertyAnnotationDefaultValue(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        return getConfigPropertyAnnotation(ctx, packageName, imports)
                .flatMap(annotationContext -> AntlrUtils.getAnnotationValue(annotationContext, "defaultValue"))
                .orElse(ConfigProperty.UNDEFINED_DEFAULT_VALUE);
    }

    private static Optional<String> getConfigPropertyAnnotationName(
            final RecordComponentContext ctx, final String packageName, final List<String> imports) {
        return getConfigPropertyAnnotation(ctx, packageName, imports)
                .flatMap(annotationContext -> AntlrUtils.getAnnotationValue(annotationContext));
    }

    private static ConfigDataPropertyDefinition createPropertyDefinition(
            RecordComponentContext ctx, final String packageName, final List<String> imports) {
        final String componentName = ctx.identifier().getText();
        final String name =
                getConfigPropertyAnnotationName(ctx, packageName, imports).orElse(componentName);
        final String defaultValue = getConfigPropertyAnnotationDefaultValue(ctx, packageName, imports);
        final String type = Optional.ofNullable(ctx.typeType().classOrInterfaceType())
                .map(c -> c.getText())
                .map(typeText -> imports.stream()
                        .filter(importText -> importText.endsWith(typeText))
                        .findAny()
                        .orElse(typeText))
                .map(typeText -> getTypeForJavaLang(typeText))
                .orElseGet(() -> ctx.typeType().primitiveType().getText());
        return new ConfigDataPropertyDefinition(componentName, name, type, defaultValue, "");
    }

    private static String getTypeForJavaLang(String type) {
        if (!type.contains(".")) {
            return String.class.getPackageName() + "." + type;
        }
        return type;
    }

    @Override
    public void enterRecordDeclaration(RecordDeclarationContext ctx) {
        final String packageName = AntlrUtils.getPackage(ctx);
        final List<String> imports = AntlrUtils.getImports(ctx);
        if (isAnnotatedWithConfigData(ctx, packageName, imports)) {
            final String recordName = ctx.identifier().getText();
            final String configPropertyNamePrefix = getConfigDataAnnotationValue(ctx, packageName, imports);
            final Set<ConfigDataPropertyDefinition> propertyDefinitions =
                    ctx.recordHeader().recordComponentList().recordComponent().stream()
                            .map(c -> createPropertyDefinition(c, packageName, imports))
                            .collect(Collectors.toSet());

            definition = new ConfigDataRecordDefinition(
                    packageName, recordName, configPropertyNamePrefix, propertyDefinitions);
        }
    }

    public ConfigDataRecordDefinition getDefinition() {
        return definition;
    }
}
