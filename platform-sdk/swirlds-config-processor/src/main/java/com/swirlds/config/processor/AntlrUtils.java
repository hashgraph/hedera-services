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

import com.swirlds.config.processor.generated.JavaParser.AnnotationContext;
import com.swirlds.config.processor.generated.JavaParser.ClassOrInterfaceModifierContext;
import com.swirlds.config.processor.generated.JavaParser.CompilationUnitContext;
import com.swirlds.config.processor.generated.JavaParser.ElementValueContext;
import com.swirlds.config.processor.generated.JavaParser.RecordComponentContext;
import com.swirlds.config.processor.generated.JavaParser.RecordDeclarationContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Utils for antlr4 parsing of Java source code
 */
public class AntlrUtils {

    private AntlrUtils() {}

    /**
     * Get all annotations for a given {@code record} declaration context
     *
     * @param ctx the antlr context of the {@code record}
     * @return all annotations as antlr context instances
     */
    @NonNull
    public static List<AnnotationContext> getAllAnnotations(@NonNull final RecordDeclarationContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        return ctx.getParent().children.stream()
                .filter(child -> child instanceof ClassOrInterfaceModifierContext)
                .map(child -> (ClassOrInterfaceModifierContext) child)
                .flatMap(modifierContext -> modifierContext.children.stream())
                .filter(child -> child instanceof AnnotationContext)
                .map(child -> (AnnotationContext) child)
                .collect(Collectors.toList());
    }

    /**
     * Get all annotations for a given {@code record} component context
     *
     * @param ctx the antlr context of the {@code record} component
     * @return all annotations as antlr context instances
     */
    @NonNull
    public static List<AnnotationContext> getAllAnnotations(@NonNull RecordComponentContext ctx) {
        return Collections.unmodifiableList(ctx.typeType().annotation());
    }

    /**
     * Search in the given annotation context for an optional annotation context for a given {@code Annotation}.
     *
     * @param <A>         the annotation type
     * @param annotation  the annotation class
     * @param annotations all possible annotations
     * @param packageName the package name of the context in that all contexts life in
     * @param imports     the imports of the context in that all contexts life in
     * @return the optional annotation
     */
    @NonNull
    public static <A extends Annotation> Optional<AnnotationContext> findAnnotationOfType(
            @NonNull final Class<A> annotation,
            @NonNull final List<AnnotationContext> annotations,
            @NonNull final String packageName,
            @NonNull final List<String> imports) {
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(annotations, "annotations must not be null");
        return annotations.stream()
                .filter(c -> c.qualifiedName().getText().endsWith(annotation.getSimpleName()))
                .filter(c -> isValid(c, annotation, packageName, imports))
                .findAny();
    }

    /**
     * Checks if the annotationContext is a valid usage of the annotation.
     *
     * @param annotationContext the annotation context
     * @param annotation        the annotation class
     * @param packageName       the package name of the context in that all contexts life in
     * @param imports           the imports of the context in that all contexts life in
     * @param <A>               the annotation type
     * @return true if the annotation is valid
     */
    private static <A extends Annotation> boolean isValid(
            @NonNull final AnnotationContext annotationContext,
            @NonNull final Class<A> annotation,
            @NonNull final String packageName,
            @NonNull final List<String> imports) {
        Objects.requireNonNull(annotationContext, "annotationContext must not be null");
        Objects.requireNonNull(annotation, "annotation must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(imports, "imports must not be null");
        return imports.contains(annotation.getName())
                || annotationContext.qualifiedName().getText().equals(annotation.getName())
                || annotationContext.qualifiedName().getText().equals(packageName + "." + annotation.getSimpleName());
    }

    /**
     * Returns the compilation unit context for a given antlr context by doing a (recursive) search in the parent
     * contexts.
     *
     * @param ctx the antlr context
     * @return the compilation unit context
     */
    @NonNull
    public static CompilationUnitContext getCompilationUnit(@NonNull final ParserRuleContext ctx) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        final ParserRuleContext parent = ctx.getParent();
        if (parent instanceof CompilationUnitContext compilationUnitContext) {
            return compilationUnitContext;
        } else {
            return getCompilationUnit(parent);
        }
    }

    /**
     * Returns all imports of a given declaration context (by going up to the compilation unit context)
     *
     * @param ctx the antlr context
     * @return all imports as strings
     */
    @NonNull
    public static List<String> getImports(@NonNull final ParserRuleContext ctx) {
        CompilationUnitContext compilationUnitContext = getCompilationUnit(ctx);
        return compilationUnitContext.importDeclaration().stream()
                .map(context -> context.qualifiedName())
                .map(name -> name.getText())
                .collect(Collectors.toList());
    }

    /**
     * Returns the package name of a given declaration context (by going up to the compilation unit context)
     *
     * @param ctx the antlr context
     * @return the package name
     */
    @NonNull
    public static String getPackage(@NonNull final RecordDeclarationContext ctx) {
        CompilationUnitContext compilationUnitContext = getCompilationUnit(ctx);
        return compilationUnitContext.packageDeclaration().qualifiedName().getText();
    }

    /**
     * Returns the value of an annotation attribute
     *
     * @param annotationContext the annotation context
     * @param identifier        the identifier of the attribute
     * @return the value of the attribute
     */
    @NonNull
    public static Optional<String> getAnnotationValue(
            @NonNull final AnnotationContext annotationContext, @NonNull final String identifier) {
        return annotationContext.elementValuePairs().elementValuePair().stream()
                .filter(p -> Objects.equals(p.identifier(), identifier))
                .map(p -> p.elementValue().getText())
                .findAny();
    }

    /**
     * Returns the value of an annotation {@code value} attribute
     *
     * @param annotationContext the annotation context
     * @return the value of the {@code value attribute
     */
    @NonNull
    public static Optional<String> getAnnotationValue(@NonNull final AnnotationContext annotationContext) {
        final ElementValueContext elementValueContext = annotationContext.elementValue();
        if (elementValueContext != null) {
            return Optional.of(elementValueContext.getText());
        }
        return getAnnotationValue(annotationContext, "value");
    }
}
