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

package com.hedera.node.app.service.file.impl.codec;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class BytesCodec implements Codec<Bytes> {

    @NonNull
    @Override
    public Bytes parse(@NonNull ReadableSequentialData input) throws IOException {
        return input.readBytes(input.readInt());
    }

    @NonNull
    @Override
    public Bytes parseStrict(@NonNull ReadableSequentialData input) throws IOException {
        return parse(input);
    }

    @Override
    public void write(@NonNull Bytes item, @NonNull WritableSequentialData output) throws IOException {
        final int length = Math.toIntExact(item.length());
        output.writeInt(length);
        output.writeBytes(item.toByteArray());
    }

    @Override
    public int measure(@NonNull ReadableSequentialData input) throws IOException {
        return input.view(4).readInt() + 4;
    }

    @Override
    public int measureRecord(Bytes item) {
        return Math.toIntExact(item.length());
    }

    @Override
    public boolean fastEquals(@NonNull Bytes item, @NonNull ReadableSequentialData input) throws IOException {
        final ReadableSequentialData comparisonView = input.view(measure(input));
        final Bytes other = parseStrict(comparisonView);
        return item.equals(other);
    }
}
