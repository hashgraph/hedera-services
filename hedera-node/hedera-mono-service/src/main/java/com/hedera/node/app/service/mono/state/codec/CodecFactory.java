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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutput;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
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
            public T parse(final @NonNull DataInput input) throws IOException {
                if (input instanceof SerializableDataInputStream in) {
                    return parser.parse(new DataInputStream(in));
                } else {
                    throw new IllegalArgumentException("Unsupported input type: " + input.getClass());
                }
            }

            @Override
            public void write(final @NonNull T item, final @NonNull DataOutput output) throws IOException {
                if (output instanceof SerializableDataOutputStream out) {
                    writer.write(item, new DataOutputStream(out));
                } else {
                    throw new IllegalArgumentException("Unsupported output type: " + output.getClass());
                }
            }

            @Override
            public int measure(final @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }

            @NonNull
            @Override
            public T parseStrict(final @NonNull DataInput dataInput) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int measureRecord(final T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean fastEquals(@NonNull T item, @NonNull DataInput input) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
