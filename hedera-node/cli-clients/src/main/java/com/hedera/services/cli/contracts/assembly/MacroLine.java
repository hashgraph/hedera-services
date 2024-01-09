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

public abstract class MacroLine implements Code {

    final int codeOffset;
    final int codeSize;
    final @NonNull String mnemonic;

    public MacroLine(int codeOffset, int codeSize, @NonNull String mnemonic) {
        Objects.requireNonNull(mnemonic);
        this.codeOffset = codeOffset;
        this.codeSize = codeSize;
        this.mnemonic = mnemonic;
    }

    @Override
    public int getCodeOffset() {
        return codeOffset;
    }

    @Override
    public int getCodeSize() {
        return codeSize;
    }

    @Override
    public @NonNull String mnemonic() {
        return mnemonic;
    }
}
