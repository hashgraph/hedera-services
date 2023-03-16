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

package com.hedera.node.app.service.token.impl.serdes;

import static java.util.Objects.requireNonNull;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class StringCodec implements Codec<String> {
    @NonNull
    @Override
    public String parse(final @NonNull ReadableSequentialData input) throws IOException {
        requireNonNull(input);
        final var len = input.readInt();
        final var bytes = new byte[len];
        input.readBytes(bytes);
        return len == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void write(final @NonNull String value, final @NonNull WritableSequentialData output) throws IOException {
        requireNonNull(value);
        requireNonNull(output);
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.writeBytes(bytes);
    }

    @Override
    public int measure(final @NonNull ReadableSequentialData input) {
        return input.readInt();
    }

    @Override
    public boolean fastEquals(final @NonNull String value, final @NonNull ReadableSequentialData input) {
        requireNonNull(value);
        requireNonNull(input);
        try {
            return value.equals(parse(input));
        } catch (final IOException ignore) {
            return false;
        }
    }

    @NonNull
    @Override
    public String parseStrict(final @NonNull ReadableSequentialData dataInput) throws IOException {
        requireNonNull(dataInput);
        return parse(dataInput);
    }

    @Override
    public int measureRecord(@NonNull final String s) {
        requireNonNull(s);
        throw new UnsupportedOperationException("Not used");
    }
}
