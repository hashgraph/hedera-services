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

package com.hedera.services.yahcli.commands.contract.evminfo;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import java.util.ArrayList;
import java.util.List;

/** Handles all (concurrently running) code recognizers during the disassembly. */
public class CodeRecognizerManager {

    public List<CodeRecognizer> recognizers = new ArrayList<>(10);
    public static final List<Class<? extends CodeRecognizer>> recognizerClasses;

    public class Distributor extends CodeRecognizer {

        public Distributor(@NonNull final Assembly assembly) {
            super(assembly);
        }

        public void begin() {
            for (final var recognizer : recognizers) recognizer.begin();
        }

        public void acceptCodeLine(@NonNull final CodeLine code) {
            for (final var recognizer : recognizers) recognizer.acceptCodeLine(code);
        }

        public void acceptDataLine(@NonNull final DataPseudoOpLine data) {
            for (final var recognizer : recognizers) recognizer.acceptDataLine(data);
        }

        @NonNull
        public CodeRecognizer.Results end() {
            var result = new CodeRecognizer.Results();
            for (var recognizer : recognizers) {
                var r = recognizer.end();
                result = result.addAll(r);
            }
            return result;
        }

        protected void reset() {}
    }

    public final @NonNull Distributor distributor;

    public CodeRecognizerManager(@NonNull final Assembly assembly) {
        for (var recognizerKlass : recognizerClasses)
            try {
                recognizers.add(
                        recognizerKlass.getDeclaredConstructor(Assembly.class).newInstance(assembly));
            } catch (ReflectiveOperationException ex) {
                System.out.printf(
                        "*** yahcli signedstate CodeRecognizerManager - something's borked with"
                                + " the code recognizer classes: %s%n",
                        ex);
                ex.printStackTrace(System.out);
            }
        distributor = new Distributor(assembly);
    }

    /**
     * Returns a CodeRecognizer that will process each line through all the registered recognizers.
     */
    @NonNull
    public CodeRecognizer getDistributor() {
        return distributor;
    }

    static {
        // Register all CodeRecognizer subclasses (see
        // https://github.com/classgraph/classgraph/wiki/Code-examples#get-class-references-for-subclasses-of-comxyzcontrol)
        try (final ScanResult scanResult = new ClassGraph()
                .enableClassInfo()
                .ignoreClassVisibility()
                .initializeLoadedClasses()
                .whitelistPackages(CodeRecognizer.class.getPackageName())
                .scan()) {

            recognizerClasses = scanResult
                    .getSubclasses(CodeRecognizer.class.getName())
                    .directOnly() // why direct subclasses only? why not?
                    .loadClasses()
                    .stream()
                    .filter(klass -> klass != Distributor.class)
                    .<Class<? extends CodeRecognizer>>map( // cast to a tighter bound
                            klass -> klass.asSubclass(CodeRecognizer.class))
                    .toList();
            // (Explicit type for `map` above needed to make IntelliJ shut up -
            // see https://stackoverflow.com/a/47473608/751579)
        }

        if (false)
            System.out.printf(
                    "Assembly: %d CodeRecognizer subclasses found: %s%n", recognizerClasses.size(), recognizerClasses);
    }
}
