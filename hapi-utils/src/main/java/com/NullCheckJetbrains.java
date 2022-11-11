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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

public class NullCheckJetbrains {

    @NotNull
    static String getNullValue() {
        return null;
    }

    @NotNull
    static String getValue() {
        return "yeah!";
    }

    static void putValue(@NotNull String value) {
        System.out.println(value);
    }

    @TestOnly
    static String maybe() {
        if (System.currentTimeMillis() % 2 == 0) {
            return "ok";
        } else {
            return null;
        }
    }

    @Nullable
    static String argh() {
        if (System.currentTimeMillis() % 2 == 0) {
            return "ok";
        } else {
            return null;
        }
    }

    public static void main(String[] args) {
        getValue();
        getNullValue();
        putValue("A");
        putValue(null);
        final String val1 = maybe();
        System.out.println("" + val1.length());
        final String val2 = argh();
        System.out.println("" + val2.length());
    }
}
