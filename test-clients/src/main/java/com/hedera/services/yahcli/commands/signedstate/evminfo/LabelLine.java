/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
import org.jetbrains.annotations.NotNull;

public record LabelLine(@NotNull String label) implements Assembly.Line {

    public static final String LABEL_PREFIX = "";
    public static final String LABEL_SUFFIX = ":";

    public void formatLine(StringBuilder sb) {
        extendWithBlanksTo(sb, Columns.LABEL.getColumn());
        sb.append(LABEL_PREFIX);
        sb.append(label);
        sb.append(LABEL_SUFFIX);
    }
}
