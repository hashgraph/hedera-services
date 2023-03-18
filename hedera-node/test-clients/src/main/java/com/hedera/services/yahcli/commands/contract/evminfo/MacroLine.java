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

public abstract class MacroLine implements Assembly.Code {

    final int codeOffset;
    final int codeSize;
    final @NonNull String mnemonic;

    protected MacroLine(final int codeOffset, final int codeSize, @NonNull String mnemonic) {
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
    @NonNull
    public String mnemonic() {
        return mnemonic;
    }
}
