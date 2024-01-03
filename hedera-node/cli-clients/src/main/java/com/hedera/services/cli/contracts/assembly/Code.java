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

package com.hedera.services.cli.contracts.assembly;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * A line of _code_ (something with bytes from the contract)
 *
 * <p>A line of _code_ in the disassembly (could be an opcode, maybe a pseudo-op like `DATA`, or
 * maybe a (discovered) macro.
 */
public interface Code extends Line {

    /** Returns the code offset of this line of code */
    int getCodeOffset();

    /** Returns the number of bytes of this line of code */
    int getCodeSize();

    /** "name" of this line */
    @NonNull
    String mnemonic();

    /** Easy identification of this line */
    default boolean isA(@NonNull final String mnemonic) {
        Objects.requireNonNull(mnemonic);
        return mnemonic().equals(mnemonic);
    }

    default int getNextCodeOffset() {
        return getCodeOffset() + getCodeSize();
    }
}
