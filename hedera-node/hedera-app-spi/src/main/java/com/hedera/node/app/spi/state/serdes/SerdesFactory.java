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

package com.hedera.node.app.spi.state.serdes;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A factory that creates {@link Serdes} implementations from various ingredients.
 *
 * <p>Mostly useful for packaging a PBJ {@code Writer} and {@code ProtoParser} into
 * a {@link Serdes} implementation.
 */
public final class SerdesFactory {
    private SerdesFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static <T> Serdes<T> newInMemorySerdes(final PbjParser<T> parser, final PbjWriter<T> writer) {
        return new Serdes<>() {
            @NonNull
            @Override
            public T parse(final @NonNull java.io.DataInput input) throws IOException {
                if (input instanceof SerializableDataInputStream in) {
                    return parser.parse(new ReadableSequentialData(in));
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported input type: " + input.getClass());
                }
            }

            @Override
            public void write(final @NonNull T item, final @NonNull java.io.DataOutput output)
                    throws IOException {
                if (output instanceof SerializableDataOutputStream out) {
                    writer.write(item, new DataOutputStream(out));
                } else {
                    throw new IllegalArgumentException(
                            "Unsupported output type: " + output.getClass());
                }
            }

            @Override
            public int measure(@NonNull java.io.DataInput input) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int typicalSize() {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull java.io.DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
