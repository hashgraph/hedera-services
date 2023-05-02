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

package com.hedera.node.app.spi.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@SuppressWarnings("Singleton")
public class StringCodec implements Codec<String> {
    public static final StringCodec SINGLETON = new StringCodec();

    private StringCodec() {}

    @NonNull
    @Override
    public String parse(final @NonNull ReadableSequentialData input) {
        Objects.requireNonNull(input);
        final var len = input.readInt();
        return len == 0 ? "" : input.readBytes(len).asUtf8String();
    }

    @NonNull
    @Override
    public String parseStrict(final @NonNull ReadableSequentialData dataInput) {
        Objects.requireNonNull(dataInput);
        return parse(dataInput);
    }

    @Override
    public void write(final @NonNull String value, final @NonNull WritableSequentialData output) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(output);
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.writeBytes(bytes);
    }

    @Override
    public int measure(final @NonNull ReadableSequentialData input) {
        return getView(input, Integer.BYTES).readInt() + Integer.BYTES;
    }

    private ReadableSequentialData getView(final ReadableSequentialData input, final int length) {
        // @todo THIS IS WRONG.
        //     Unfortunately ReadableSequentialData.view is implemented incorrectly and
        //     consumes the internal stream.  What we should have, once ReadableSequentialData is fixed,
        //     is something like this (in fact we should inline the call and remove this method):
        //         input.view(length)
        //     If we call input.view now, however, we break parsing, so we pretend it will work.
        return input;
    }

    @Override
    public boolean fastEquals(final @NonNull String value, final @NonNull ReadableSequentialData input) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(input);
        return value.equals(parse(getView(input, measure(input))));
    }

    @Override
    public int measureRecord(@NonNull final String value) {
        Objects.requireNonNull(value);
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length + Integer.BYTES;
    }
}
