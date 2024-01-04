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

package com.hedera.services.cli.contracts;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public enum DisassemblyOption {
    DISPLAY_CODE_OFFSET,
    DISPLAY_OPCODE_HEX,
    DO_NOT_DECODE_BEFORE_METADATA,
    FETCH_SELECTOR_NAMES,
    LIST_MACROS,
    RECOGNIZE_CODE_SEQUENCES,
    TRACE_RECOGNIZERS,
    WITH_BYTECODE,
    WITH_METRICS,
    WITH_RAW_DISASSEMBLY,
    DISPLAY_SELECTORS;

    @NonNull
    private static final Set<DisassemblyOption> current = EnumSet.noneOf(DisassemblyOption.class);

    public static boolean isOn(@NonNull final DisassemblyOption option) {
        Objects.requireNonNull(option);
        return current.contains(option);
    }

    static void setOptions(@NonNull final Set<DisassemblyOption> options) {
        Objects.requireNonNull(options);
        current.addAll(options);
    }
}
