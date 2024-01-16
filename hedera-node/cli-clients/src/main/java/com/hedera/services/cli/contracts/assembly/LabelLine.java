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

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/** Represents a label in the generated assembly (a named offset in the code) */
public record LabelLine(int codeOffset, @NonNull String label, @NonNull String comment) implements Code {

    public static final String LABEL_PREFIX = "";
    public static final String LABEL_SUFFIX = ":";

    public LabelLine {
        Objects.requireNonNull(label);
        Objects.requireNonNull(comment);
    }

    public LabelLine(int codeOffset, @NonNull String label) {
        this(codeOffset, label, "");
    }

    @Override
    public void formatLine(@NonNull final StringBuilder sb) {
        Objects.requireNonNull(sb);
        extendWithBlanksTo(sb, Columns.LABEL.getColumn());
        sb.append(LABEL_PREFIX);
        sb.append(label);
        sb.append(LABEL_SUFFIX);

        if (!comment.isEmpty()) {
            extendWithBlanksTo(sb, Columns.EOL_COMMENT.getColumn());
            sb.append(EOL_COMMENT_PREFIX);
            sb.append(comment);
        }
    }

    @Override
    public int getCodeOffset() {
        return codeOffset;
    }

    @Override
    public int getCodeSize() {
        return 0;
    }

    @Override
    @NonNull
    public String mnemonic() {
        return LABEL_PREFIX + label + LABEL_SUFFIX;
    }
}
