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

import static com.swirlds.config.processor.ConfigProcessorConstants.CONSTANTS_CLASS_SUFFIX;

import com.google.auto.service.AutoService;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes(ConfigProcessorConstants.CONFIG_DATA_ANNOTATION)
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class ConfigDataAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(@NonNull Set<? extends TypeElement> annotations, @NonNull RoundEnvironment roundEnv) {
        Objects.requireNonNull(roundEnv, "annotations must not be null");
        Objects.requireNonNull(roundEnv, "roundEnv must not be null");

        if (annotations.isEmpty()) {
            return false;
        }
        final String executionPath = System.getProperty("user.dir");
        final Path configDocumentationFile = Paths.get(executionPath, "build/docs/config.md");
        try {
            Files.deleteIfExists(configDocumentationFile);
        } catch (final IOException e) {
            throw new RuntimeException("Error while deleting " + configDocumentationFile, e);
        }
        configDocumentationFile.toFile().getParentFile().mkdirs();

        log("Config Data Annotation Processor started...");
        try {
            annotations.stream()
                    .flatMap(annotation -> roundEnv.getElementsAnnotatedWith(annotation).stream())
                    .filter(element -> element.getKind() == ElementKind.RECORD)
                    .filter(element -> element instanceof TypeElement)
                    .map(TypeElement.class::cast)
                    .forEach(typeElement -> handleTypeElement(typeElement, configDocumentationFile));
            return true;
        } catch (final Exception e) {
            log("Error while processing annotations: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void handleTypeElement(
            @NonNull final TypeElement typeElement, @NonNull final Path configDocumentationFile) {
        final String simpleClassName = typeElement.getSimpleName().toString();
        final String fileName = simpleClassName + ConfigProcessorConstants.JAVA_FILE_EXTENSION;

        final String packageName = processingEnv
                .getElementUtils()
                .getPackageOf(typeElement)
                .getQualifiedName()
                .toString();
        log("handling: " + fileName + " in " + packageName);
        try {
            final FileObject recordSource = getSource(fileName, packageName);
            final ConfigDataRecordDefinition recordDefinition = ConfigRecordParser.parse(recordSource);
            final JavaFileObject constantsSourceFile = getConstantSourceFile(packageName, simpleClassName, typeElement);

            ConstantClassFactory.doWork(recordDefinition, constantsSourceFile);

            DocumentationFactory.doWork(recordDefinition, configDocumentationFile);

        } catch (final IOException e) {
            throw new RuntimeException("Error while handling " + typeElement, e);
        }
    }

    @NonNull
    private JavaFileObject getConstantSourceFile(
            @NonNull String packageName, @NonNull String simpleClassName, @NonNull TypeElement originatingElement)
            throws IOException {
        Objects.requireNonNull(packageName, "packageName must not be null");
        Objects.requireNonNull(simpleClassName, "simpleClassName must not be null");
        Objects.requireNonNull(originatingElement, "originatingElement must not be null");

        final String constantsClassName = packageName + "." + simpleClassName + CONSTANTS_CLASS_SUFFIX;
        return processingEnv.getFiler().createSourceFile(constantsClassName, originatingElement);
    }

    @NonNull
    private FileObject getSource(@NonNull String fileName, @NonNull String packageName) throws IOException {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(packageName, "packageName must not be null");

        return processingEnv.getFiler().getResource(StandardLocation.SOURCE_PATH, packageName, fileName);
    }

    protected void log(@NonNull String message) {
        Objects.requireNonNull(message, "message must not be null");

        processingEnv.getMessager().printMessage(Kind.NOTE, message);
    }
}
