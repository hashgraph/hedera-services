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
package com.hedera.services.yahcli.commands.signedstate;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HexFormat;
import java.util.regex.Pattern;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Converts a hex string command line argument to a byte array
 *
 * <p>For Picocli library - implements a type-specific converter from hex string to bytes.
 */
public class HexStringConverter implements ITypeConverter<HexStringConverter.Bytes> {

    // Unfortunately can't return `byte[]` directly from the custom type converter because Piccoli
    // gets confused: When the argument is declared (at the command class) it thinks it wants a
    // multi-value type (even if `arity=1` is specified). Thus: need to wrap it in a stupid class.
    // Would have used a record but Sonarlint complains (properly) that records with array members
    // need overrides for `equals`/`hashcode`/`toString`, and at that point, why bother?
    // See https://stackoverflow.com/a/74207195/751579.
    public static class Bytes {
        public final @NonNull byte[] contents;

        public Bytes(final byte[] b) {
            if (null == b)
                throw new TypeConversionException("-b bytecode missing an array value (somehow)");
            contents = b;
        }
    }

    /** "Converts the specified command line argument value to some domain object" */
    @Override
    public @NonNull Bytes convert(@NonNull final String value) {
        if (0 != value.length() % 2)
            throw new TypeConversionException("-b bytecode must have even number of hexits");
        if (!Pattern.compile("[0-9a-fA-F]+").matcher(value).matches())
            throw new TypeConversionException("-b bytecode has invalid characters (not hexits)");
        return new Bytes(HexFormat.of().parseHex(value));
    }
}
