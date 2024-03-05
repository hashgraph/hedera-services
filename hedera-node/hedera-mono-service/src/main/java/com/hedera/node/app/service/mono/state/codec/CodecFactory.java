/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * A factory that creates {@link com.hedera.pbj.runtime.Codec} implementations from various ingredients.
 *
 * <p>Mostly useful for packaging a PBJ {@code Writer} and {@code ProtoParser} into
 * a {@link com.hedera.pbj.runtime.Codec} implementation.
 */
public final class CodecFactory {
    private CodecFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> Codec<T> newInMemoryCodec(final PbjParser<T> parser, final PbjWriter<T> writer) {
        return new Codec<>() {
            @NonNull
            @Override
            public T parse(final @NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
                    throws ParseException {
                if (input instanceof ReadableStreamingData in) {
                    return parser.parse(in);
                } else {
                    throw new IllegalArgumentException("Unsupported input type: " + input.getClass());
                }
            }

            @Override
            public void write(final @NonNull T item, final @NonNull WritableSequentialData output) throws IOException {
                if (output instanceof WritableStreamingData out) {
                    writer.write(item, out);
                } else {
                    throw new IllegalArgumentException("Unsupported output type: " + output.getClass());
                }
            }

            @Override
            public int measure(final @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public T parseStrict(final @NonNull ReadableSequentialData dataInput) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(final T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull ReadableSequentialData input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
