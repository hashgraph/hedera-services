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
package com;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

public class NullCheck {

    @NonNull
    static String getNullValue() {
        return null;
    }

    @NonNull
    static String getValue() {
        return "yeah!";
    }

    static void putValue(@NonNull String value) {
        Objects.requireNonNull(value);
    }

    @CheckForNull
    static String maybe() {
        if (System.currentTimeMillis() % 2 == 0) {
            return "ok";
        } else {
            return null;
        }
    }

    @Nullable
    static String possible() {
        if (System.currentTimeMillis() % 2 == 0) {
            return null;
        } else {
            return "ok";
        }
    }

    public static void main(String[] args) {
        getValue();
        getNullValue();
        putValue("A");
        putValue(null);
        final String val1 = maybe();
        Objects.requireNonNull("" + val1.length());
        final String val2 = possible();
        Objects.requireNonNull("" + val2.length());
        @Nullable final String val3 = possible();
        Objects.requireNonNull("" + val3.length());
    }
}
