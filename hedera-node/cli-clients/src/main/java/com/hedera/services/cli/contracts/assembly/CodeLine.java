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

import static com.hedera.services.cli.contracts.assembly.Constants.EOL_COMMENT_PREFIX;

import com.hedera.services.cli.contracts.DisassemblyOption;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an instruction line in the generated assembly
 *
 * <p>Instruction lines have an opcode, an optional operand (an array of bytes), an offset of where
 * the instruction is in the bytecode, and an optional comment.
 */
public record CodeLine(int codeOffset, int opcode, byte[] operandBytes, String eolComment) implements Code {

    public CodeLine {
        opcode = opcode & 0xFF; // convert to unsigned (in case it's not)
        // De-null optional arguments
        operandBytes = null != operandBytes ? operandBytes : new byte[0];
        eolComment = null != eolComment ? eolComment : "";
    }

    @Override
    public @NonNull String mnemonic() {
        return Opcodes.getOpcode(opcode).mnemonic();
    }

    @Override
    public void formatLine(@NonNull final StringBuilder sb) {
        Objects.requireNonNull(sb);
        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_CODE_OFFSET)) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append("%5X".formatted(codeOffset));
        }

        if (DisassemblyOption.isOn(DisassemblyOption.DISPLAY_OPCODE_HEX)) {
            extendWithBlanksTo(sb, Columns.OPCODE.getColumn());
            sb.append("  %02X".formatted(opcode()));
        }

        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(Opcodes.getOpcode(opcode()).mnemonic());
        if (0 != operandBytes.length) {
            extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
            sb.append(hexer().formatHex(operandBytes));
        }
        if (!eolComment.isEmpty()) {
            if (Columns.EOL_COMMENT.getColumn() - 1 > sb.length()) {
                extendWithBlanksTo(sb, Columns.EOL_COMMENT.getColumn());
            } else {
                sb.append("  ");
            }
            sb.append(EOL_COMMENT_PREFIX);
            sb.append(eolComment);
        }
    }

    @Override
    public int getCodeOffset() {
        return codeOffset;
    }

    @Override
    public int getCodeSize() {
        return 1 + operandBytes.length;
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        return o instanceof CodeLine other
                && codeOffset == other.codeOffset
                && opcode == other.opcode
                && Arrays.equals(operandBytes, other.operandBytes)
                && eolComment.equals(other.eolComment);
    }

    @Override
    public int hashCode() {
        int result = codeOffset;
        result = 31 * result + opcode;
        result = 31 * result + Arrays.hashCode(operandBytes);
        result = 31 * result + eolComment.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "CodeLine[codeOffset=%04X, opcode=%d (%02X), operandBytes=%s, eolComment='%s']"
                .formatted(codeOffset, opcode, opcode(), hexer().formatHex(operandBytes), eolComment);
    }
}
