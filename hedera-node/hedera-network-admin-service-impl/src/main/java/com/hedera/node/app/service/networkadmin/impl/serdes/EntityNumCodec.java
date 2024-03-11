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

package com.hedera.node.app.service.networkadmin.impl.serdes;

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class EntityNumCodec implements Codec<EntityNum> {
    @NonNull
    @Override
    public EntityNum parse(final @NonNull ReadableSequentialData input, final boolean strictMode, final int maxDepth)
            throws ParseException {
        return new EntityNum(input.readInt());
    }

    @Override
    public void write(final @NonNull EntityNum item, final @NonNull WritableSequentialData output) throws IOException {
        output.writeInt(item.intValue());
    }

    @Override
    public int measure(final @NonNull ReadableSequentialData input) throws ParseException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(final @NonNull EntityNum entityNum) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(final @NonNull EntityNum item, final @NonNull ReadableSequentialData input) {
        throw new UnsupportedOperationException();
    }

    @NonNull
    @Override
    public EntityNum parseStrict(@NonNull ReadableSequentialData dataInput) throws ParseException {
        return parse(dataInput);
    }
}
