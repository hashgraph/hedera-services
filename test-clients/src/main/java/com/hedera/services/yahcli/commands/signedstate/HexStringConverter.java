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
package com.hedera.services.yahcli.commands.signedstate;

import static java.lang.Byte.toUnsignedInt;

import java.util.HexFormat;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

public class HexStringConverter implements ITypeConverter<HexStringConverter.Bytes> {

    // Unfortunately can't return `short[]` directly from the custom type converter because Piccoli
    // gets confused: When the argument is declared (at the command class) it thinks it wants a
    // multi-value type (even if `arity=1` is specified). Thus: need to wrap it in a stupid class.
    // Would have used a record but Sonarlint complains (properly) that records with array members
    // need overrides for `equals`/`hashcode`/`toString`, and at that point, why bother?
    // See https://stackoverflow.com/a/74207195/751579.
    public static class Bytes {
        final int[] contents;

        public Bytes(int[] b) {
            contents = b;
        }
    }

    @Contract(pure = true)
    @Override
    public @NotNull Bytes convert(String value) throws Exception {
        if (0 != value.length() % 2)
            throw new TypeConversionException("-b bytecode must have even number of hexits");
        if (!Pattern.compile("[0-9a-fA-F]+").matcher(value).matches())
            throw new TypeConversionException("-b bytecode has invalid characters (not hexits)");
        return new Bytes(toUnsignedBuffer(HexFormat.of().parseHex(value)));
    }

    @Contract(pure = true)
    private int @NotNull [] toUnsignedBuffer(byte @NotNull [] signedBuffer) {
        // In Java, `byte[]` is a buffer of _signed_ bytes.  In the real world (and every other
        // common programming language) a byte array is a buffer of _unsigned_ bytes the way network
        // programmers and device I/O programmers and crypto programmers and bytecode interpreter
        // programmers in fact, _every_ kind of programmer! - expects.  So here we take our buffer
        // of signed bytes and turn it into a buffer of _unsigned_ bytes, so all will be right with
        // the world.  (Takes 2x space, but that's a small price to pay for clearing away
        // unnecessary cognitive effort by all programmers trying to understand this app.) (Oh, and
        // it also means the compiler/JITer won't know that the array elements are restricted to
        // 0..255, oh well.)
        // **UPDATE:** I give up.  Just suck it up and use `int[]`.  4x space.  Too much trouble
        // otherwise.
        var r = new int[signedBuffer.length];
        int i = 0;
        for (byte b : signedBuffer) r[i++] = toUnsignedInt(b);
        return r;
    }
}
