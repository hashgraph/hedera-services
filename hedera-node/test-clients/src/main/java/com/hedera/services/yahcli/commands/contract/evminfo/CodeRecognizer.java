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

import com.hedera.services.yahcli.commands.contract.evminfo.Assembly.Code;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class CodeRecognizer {

    // A limitation on code recognizers is that they get one pass through the code, in order.
    // TODO: A _second_ major limitation is that Results only holds a List<Code> of replacements,
    //       it should really hold an Editor.

    @NonNull
    protected final Assembly assembly;

    protected CodeRecognizer(@NonNull final Assembly assembly) {
        this.assembly = assembly;
    }

    public void begin() {}

    public void acceptCodeLine(@NonNull final CodeLine code) {
        reset();
    }

    public void acceptDataLine(@NonNull final DataPseudoOpLine data) {
        reset();
    }

    public record Results(@NonNull List<Code> codeLineReplacements, @NonNull Map<String, Object> properties) {
        public Results() {
            this(new ArrayList<>(), new HashMap<>());
        }

        public @NonNull Results withReplacements(@NonNull List<Code> replacements) {
            codeLineReplacements.addAll(replacements);
            return new Results(codeLineReplacements, properties);
        }

        public @NonNull Results withProperty(@NonNull String property, @NonNull Object value) {
            properties.put(property, value);
            return new Results(codeLineReplacements, properties);
        }

        public @NonNull Results addAll(@NonNull Results r) {
            codeLineReplacements.addAll(r.codeLineReplacements);
            properties.putAll(r.properties);
            return new Results(codeLineReplacements, properties);
        }
    }

    @NonNull
    public Results end() {
        return new Results();
    }

    protected abstract void reset();
}
