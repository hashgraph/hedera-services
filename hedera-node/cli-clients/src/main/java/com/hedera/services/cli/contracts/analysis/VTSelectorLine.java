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
import com.hedera.services.cli.contracts.assembly.LabelLine;
import com.hedera.services.cli.contracts.assembly.MacroLine;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public final class VTSelectorLine extends MacroLine {

    public final long selector;
    public final int methodOffset;

    public long getSelector() {
        return selector;
    }

    public int getMethodOffset() {
        return methodOffset;
    }

    public VTSelectorLine(int codeOffset, int codeSize, long selector, int methodOffset) {
        super(codeOffset, codeSize, "VTSelector");
        this.selector = selector;
        this.methodOffset = methodOffset;
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
        sb.append(formatSelector());
        sb.append("  ");
        sb.append(formatOffset(methodOffset));
    }

    @NonNull
    LabelLine asLabel() {
        return new LabelLine(methodOffset, formatSelector(), "from @" + formatOffset(getCodeOffset()));
    }

    @NonNull
    String formatSelector() {
        return hexer().toHexDigits(selector, 8);
    }

    @NonNull
    String formatOffset(int offset) {
        return hexer().toHexDigits(offset, 4);
    }
}
