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

package com.hedera.node.app.workflows;

import com.hedera.pbj.runtime.ParseException;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This is a temporary workaround for an unexpected behavior in PBJ. A {@link ParseException} may be wrapped in
 * another {@link ParseException} as its cause. This class provides a method to get the root cause of the
 * {@link ParseException}.
 *
 * <p>If we agree that {@link ParseException} should not be wrapped in another {@link ParseException}, then this class
 * can be removed.
 */
public class ParseExceptionWorkaround {

    private ParseExceptionWorkaround() {}

    /**
     * Returns the root cause of the given {@link ParseException}.
     *
     * @param ex the {@link ParseException} to get the root cause of
     * @return the root cause of the given {@link ParseException}
     */
    public static Throwable getParseExceptionCause(@NonNull final ParseException ex) {
        var cause = ex.getCause();
        while (cause instanceof ParseException) {
            cause = cause.getCause();
        }
        return cause;
    }
}
