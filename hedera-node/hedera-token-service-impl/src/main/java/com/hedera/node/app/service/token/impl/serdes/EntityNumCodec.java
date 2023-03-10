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

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class EntityNumCodec implements Codec<EntityNum> {
    @NonNull
    @Override
    public EntityNum parse(final @NonNull DataInput input) throws IOException {
        requireNonNull(input);
        return new EntityNum(input.readInt());
    }

    @NonNull
    @Override
    public EntityNum parseStrict(@NonNull DataInput dataInput) throws IOException {
        return parse(requireNonNull(dataInput));
    }

    @Override
    public void write(final @NonNull EntityNum item, final @NonNull DataOutput output) throws IOException {
        requireNonNull(item);
        requireNonNull(output);
        output.writeInt(item.intValue());
    }

    @Override
    public int measure(final @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(EntityNum entityNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(final @NonNull EntityNum item, final @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }
}
