/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.statedumpers.utils;

import edu.umd.cs.findbugs.annotations.NonNull;

public class FieldBuilder {
    final StringBuilder sb;
    final String fieldSeparator;

    public FieldBuilder(@NonNull final String fieldSeparator) {
        this.sb = new StringBuilder();
        this.fieldSeparator = fieldSeparator;
    }

    public void append(@NonNull final String v) {
        sb.append(v);
        sb.append(fieldSeparator);
    }

    @Override
    @NonNull
    public String toString() {
        if (sb.length() > fieldSeparator.length()) sb.setLength(sb.length() - fieldSeparator.length());
        return sb.toString();
    }
}
