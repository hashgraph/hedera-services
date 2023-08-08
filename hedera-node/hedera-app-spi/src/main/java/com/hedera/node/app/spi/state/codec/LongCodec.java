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
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

@SuppressWarnings("Singleton")
public class LongCodec implements Codec<Long> {
    public static final LongCodec SINGLETON = new LongCodec();

    private LongCodec() {}

    @NonNull
    @Override
    public Long parse(@NonNull ReadableSequentialData input) {
        Objects.requireNonNull(input);
        return Long.valueOf(input.readLong());
    }

    @NonNull
    @Override
    public Long parseStrict(@NonNull ReadableSequentialData input) {
        Objects.requireNonNull(input);
        return Long.valueOf(input.readLong());
    }

    @Override
    public void write(@NonNull Long value, @NonNull WritableSequentialData output) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(output);
        output.writeLong(value);
    }

    @Override
    public int measure(@Nullable ReadableSequentialData input) {
        return Long.BYTES;
    }

    @Override
    public int measureRecord(@Nullable Long aLong) {
        return Long.BYTES;
    }

    @Override
    public boolean fastEquals(@NonNull Long value, @NonNull ReadableSequentialData input) {
        Objects.requireNonNull(value);
        Objects.requireNonNull(input);
        return value.equals(parse(getView(input, measure(input))));
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
}
