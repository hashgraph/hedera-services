/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.yahcli.commands.signedstate.evminfo;

import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Columns;
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Options;
import java.util.Arrays;
import org.jetbrains.annotations.Contract;

public record CodeLine(int codeOffset, int opcode, int[] operandBytes, String eolComment)
        implements Assembly.Line, Assembly.Code {

    public CodeLine {
        // De-null arguments
        operandBytes = null != operandBytes ? operandBytes : new int[0];
        eolComment = null != eolComment ? eolComment : "";
    }

    @Contract(pure = true)
    public void formatLine(StringBuilder sb) {
        if (Options.DISPLAY_CODE_OFFSET.getValue()) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append(String.format("%5X", codeOffset));
        }

        if (Options.DISPLAY_OPCODE_HEX.getValue()) {
            extendWithBlanksTo(sb, Columns.OPCODE.getColumn());
            sb.append(String.format("  %02X", opcode));
        }

        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(Opcodes.byOpcode.get(opcode).mnemonic());
        if (0 != operandBytes.length) {
            extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
            sb.append(Utility.toHex(operandBytes));
        }
        if (!eolComment.isEmpty()) {
            if (Columns.EOL_COMMENT.getColumn() - 1 > sb.length()) {
                extendWithBlanksTo(sb, Columns.EOL_COMMENT.getColumn());
            } else {
                sb.append("  ");
            }
            sb.append(Assembly.EOL_COMMENT_PREFIX);
            sb.append(eolComment);
        }
    }

    @Contract(pure = true)
    public int getCodeOffset() {
        return codeOffset;
    }

    @Contract(pure = true)
    public int getCodeSize() {
        return 1 + operandBytes.length;
    }

    @Contract(pure = true)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof CodeLine other) {
            return codeOffset == other.codeOffset
                    && opcode == other.opcode
                    && Arrays.equals(operandBytes, other.operandBytes)
                    && eolComment.equals(other.eolComment);
        } else return false;
    }

    @Contract(pure = true)
    @Override
    public int hashCode() {
        int result = codeOffset;
        result = 31 * result + opcode;
        result = 31 * result + Arrays.hashCode(operandBytes);
        result = 31 * result + eolComment.hashCode();
        return result;
    }

    @Contract(pure = true)
    @Override
    public String toString() {
        return String.format(
                "CodeLine[codeOffset=%04X, opcode=%02X, operandBytes=%s, eolComment='%s']",
                codeOffset, opcode, Utility.toHex(operandBytes), eolComment);
    }
}
