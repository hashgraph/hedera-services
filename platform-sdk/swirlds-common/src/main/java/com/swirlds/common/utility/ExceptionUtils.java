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

package com.swirlds.common.utility;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public enum ExceptionUtils {
// this is a utility class, so no enum values
;

    public static String getStackTrace(@NonNull final Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable is null");
        final StringWriter stringWriter = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(stringWriter, true);

        throwable.printStackTrace(printWriter);

        return stringWriter.getBuffer().toString();
    }
}
