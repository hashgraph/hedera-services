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
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a directive in the generated assembly.
 *
 * <p>Directives are commands to the assembler (and notes to the reader) such as `BEGIN` and `END`.
 */
public record DirectiveLine(
        @NonNull String directive, @NonNull String operand, @NonNull String comment)
        implements Assembly.Line {

    /**
     * It isn't _necessary_ that the directives be listed here - _any_ string can be passed as the
     * mnemonic of this Directive. But it's convenient for common/popular directives since you can
     * control the spelling this way ...
     */
    public enum Kind {
        BEGIN,
        END
    }

    public DirectiveLine(@NonNull final String directive) {
        this(directive, "", "");
    }

    public DirectiveLine(@NonNull final Kind directive) {
        this(directive.name(), "", "");
    }

    public DirectiveLine(@NonNull final String directive, @NonNull final String comment) {
        this(directive, "", comment);
    }

    public DirectiveLine(@NonNull final Kind directive, @NonNull final String comment) {
        this(directive.name(), "", comment);
    }

    @Override
    public void formatLine(StringBuilder sb) {
        if (!directive.isEmpty()) {
            extendWithBlanksTo(sb, Columns.MNEMONIC.getColumn());
            sb.append(directive);
        }
        if (!operand.isEmpty()) {
            extendWithBlanksTo(sb, Columns.OPERAND.getColumn());
            sb.append(operand);
        }
        if (!comment.isEmpty()) {
            extendWithBlanksTo(sb, Columns.EOL_COMMENT.getColumn());
            sb.append(Assembly.EOL_COMMENT_PREFIX);
            sb.append(comment);
        }
    }
}
