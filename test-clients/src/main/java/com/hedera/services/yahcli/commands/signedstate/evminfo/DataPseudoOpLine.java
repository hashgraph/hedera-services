/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.hedera.services.yahcli.commands.signedstate.evminfo.Assembly.Variant;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;

/**
 * Represents a `DATA` pseudo op in the generated assembly
 *
 * <p>A DATA line is just a blob of bytes. It disassembles to the hex representation of that blob.
 * Because it is actual code bytes it has a code offset.
 */
public record DataPseudoOpLine(
        int codeOffset, @NonNull Kind kind, byte[] operandBytes, String eolComment)
        implements Assembly.Line, Assembly.Code {

    public enum Kind {
        DATA,
        METADATA,
        DATA_OVERRUN
    }

    public DataPseudoOpLine {
        // De-null operands
        operandBytes = null != operandBytes ? operandBytes : new byte[0];
        eolComment = null != eolComment ? eolComment : "";
    }

    // NOTTODO: There's a LOT of commonality here with CodeLine! like maybe this should be a
    // subclass (except: can't do that because it's a record, and this doesn't seem like the
    // kind of thing that should be a default method of the interface ...)
    @Override
    public void formatLine(StringBuilder sb) {
        if (Variant.DISPLAY_CODE_OFFSET.getValue()) {
            extendWithBlanksTo(sb, Columns.CODE_OFFSET.getColumn());
            sb.append(String.format("%5X", codeOffset));
        }
        extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
        sb.append(kind.name());

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
            sb.append(Assembly.EOL_COMMENT_PREFIX);
            sb.append(eolComment);
        }
    }

    @Override
    public int getCodeOffset() {
        return codeOffset;
    }

    @Override
    public int getCodeSize() {
        return operandBytes.length;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DataPseudoOpLine other
                && codeOffset == other.codeOffset
                && Arrays.equals(operandBytes, other.operandBytes)
                && eolComment.equals(other.eolComment)
                && kind.equals(other.kind);
    }

    @Override
    public int hashCode() {
        int result = codeOffset;
        result = 31 * result + Arrays.hashCode(operandBytes);
        result = 31 * result + eolComment.hashCode();
        result = 31 * result + kind.hashCode();
        return result;
    }

    @Override
    public @NonNull String toString() {
        return String.format(
                "CodeLine[codeOffset=%04X, operandBytes=%s, eolComment='%s', kind=%s]",
                codeOffset, hexer().formatHex(operandBytes), eolComment, kind.toString());
    }
}
