/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.contracts.analysis;

import com.hedera.services.cli.contracts.assembly.CodeLine;
import com.hedera.services.cli.contracts.assembly.DataPseudoOpLine;
import com.hedera.services.cli.contracts.assembly.Line;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;

public class MethodAnalysis {

    final List<Line> lines;

    public MethodAnalysis(@NonNull final List<Line> lines) {
        Objects.requireNonNull(lines);
        this.lines = lines;
    }

    /** Recognize and analyze bytecode sequences related to methods */
    public @NonNull CodeRecognizer.Results analyze() {
        final CodeRecognizerManager codeRecognizerManager = new CodeRecognizerManager();
        final CodeRecognizer recognizer = codeRecognizerManager.getDistributor();

        recognizer.begin();
        for (final var line : lines) {
            if (line instanceof DataPseudoOpLine data) recognizer.acceptDataLine(data);
            else if (line instanceof CodeLine code) recognizer.acceptCodeLine(code);
        }
        return recognizer.end();
    }
}
