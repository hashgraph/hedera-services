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

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

@SupportedAnnotationTypes("com.swirlds.config.api.ConfigData")
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@AutoService(Processor.class)
public class ConfigDataAnnotationProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        processingEnv.getMessager().printMessage(Kind.WARNING, "TADA");

        for (TypeElement annotation : annotations) {
            Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(annotation);
            annotatedElements.forEach(element -> {

                if (element.getKind() == ElementKind.RECORD) {
                    TypeElement typeElement = (TypeElement) element;
                    String simpleClassName = typeElement.getSimpleName().toString();
                    String fileName = simpleClassName + ".java";
                    String packageName = typeElement.getQualifiedName().toString().replace("." + simpleClassName, "");
                    System.out.println("handling: " + fileName + " in " + packageName);

                    try {
                        FileObject resource = processingEnv.getFiler()
                                .getResource(StandardLocation.SOURCE_PATH, packageName, fileName);
                        Path.of(resource.toUri()).normalize().toAbsolutePath();
                        new DocumentationFactory().doWork(resource);

                        JavaFileObject constantsSourceFile = processingEnv.getFiler()
                                .createSourceFile(packageName + "." + simpleClassName + "Constants", typeElement);
                        new ConstantClassFactory().doWork(resource, constantsSourceFile);

                        //new BufferedReader(resource.openReader(true)).lines().forEach(System.out::println);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        }
        return true;
    }
}