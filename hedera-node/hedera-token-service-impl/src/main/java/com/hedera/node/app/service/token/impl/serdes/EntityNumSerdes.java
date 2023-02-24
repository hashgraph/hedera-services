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

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public class EntityNumSerdes implements Serdes<EntityNum> {
    @NonNull
    @Override
    public EntityNum parse(final @NonNull DataInput input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            return new EntityNum(in.readInt());
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(final @NonNull EntityNum item, final @NonNull DataOutput output) throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            out.writeInt(item.intValue());
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(final @NonNull DataInput input) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int typicalSize() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(final @NonNull EntityNum item, final @NonNull DataInput input) {
        throw new UnsupportedOperationException();
    }
}
