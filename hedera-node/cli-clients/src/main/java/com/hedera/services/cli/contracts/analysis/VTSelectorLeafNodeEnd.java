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

import com.hedera.services.cli.contracts.DisassemblyOption;
import com.hedera.services.cli.contracts.assembly.Columns;
import com.hedera.services.cli.contracts.assembly.MacroLine;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public final class VTSelectorLeafNodeEnd extends MacroLine {

    public static final String FAIL_DESTINATION = "VTSelectorNotFound";
    public final int failOffset;

    public int getFailOffset() {
        return failOffset;
    }

    public VTSelectorLeafNodeEnd(int codeOffset, int codeSize, int failOffset) {
        super(codeOffset, codeSize, FAIL_DESTINATION);
        this.failOffset = failOffset;
    }

    @Override
    public void formatLine(@NonNull final StringBuilder sb) {
        Objects.requireNonNull(sb);
        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_CODE_OFFSET)) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append("%5X".formatted(getCodeOffset()));
        }

        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(mnemonic());

        extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
        sb.append(formatOffset(failOffset));
    }

    @NonNull
    String formatOffset(int offset) {
        return hexer().toHexDigits(offset, 4);
    }
}
