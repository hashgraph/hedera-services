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

import org.jetbrains.annotations.NotNull;

public class Utility {

    // Why this roll-your-own implementation of int->hex when there are a dozen
    // already in the framework?  Couldn't find one that _always_ output the
    // highest nibble, even if 0.  E.g., `String.format`'s "%X" doesn't ... `HexFormat`
    // does it ... but only for `byte[]` not `int[]` ...  (correct me please if
    // I'm wrong ...)
    public static @NotNull String toHex(int @NotNull [] byteBuffer, int from) {
        final String hexits = "0123456789ABCDEF";
        var sb = new StringBuilder(2 * byteBuffer.length);
        for (int i = from; i < byteBuffer.length; i++) {
            var byt = byteBuffer[i];
            sb.append(hexits.charAt(byt >> 4));
            sb.append(hexits.charAt(byt & 0xf));
        }
        return sb.toString();
    }

    public static @NotNull String toHex(int @NotNull [] byteBuffer) {
        return toHex(byteBuffer, 0);
    }

    private Utility() {}
}
