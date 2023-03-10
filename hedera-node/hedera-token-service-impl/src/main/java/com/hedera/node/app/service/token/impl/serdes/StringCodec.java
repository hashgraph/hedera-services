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

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class StringCodec implements Codec<String> {
    @NonNull
    @Override
    public String parse(@NonNull DataInput input) throws IOException {
        final var len = input.readInt();
        final var bytes = new byte[len];
        input.readBytes(bytes);
        return len == 0 ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void write(@NonNull String value, @NonNull DataOutput output) throws IOException {
        final var bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.writeBytes(bytes);
    }

    @Override
    public int measure(@NonNull DataInput input) throws IOException {
        return input.readInt();
    }

    @Override
    public boolean fastEquals(@NonNull String value, @NonNull DataInput input) {
        try {
            return value.equals(parse(input));
        } catch (final IOException ignore) {
            return false;
        }
    }

    @NonNull
    @Override
    public String parseStrict(@NonNull com.hedera.pbj.runtime.io.DataInput dataInput) throws IOException {
        return parse(dataInput);
    }

    @Override
    public int measureRecord(String s) {
        throw new UnsupportedOperationException("Not used");
    }
}
