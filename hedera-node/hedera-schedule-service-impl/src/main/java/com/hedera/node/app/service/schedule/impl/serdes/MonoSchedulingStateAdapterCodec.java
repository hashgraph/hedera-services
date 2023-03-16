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

package com.hedera.node.app.service.schedule.impl.serdes;

import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class MonoSchedulingStateAdapterCodec implements Codec<MerkleScheduledTransactionsState> {

    @NonNull
    @Override
    public MerkleScheduledTransactionsState parseStrict(
            @NonNull final ReadableSequentialData readableSequentialData) throws IOException {
        throw new AssertionError("Not implemented");
    }

    @NonNull
    @Override
    public MerkleScheduledTransactionsState parse(final @NonNull ReadableSequentialData input) throws IOException {
        if (input instanceof SerializableDataInputStream in) {
            final var context = new MerkleScheduledTransactionsState();
            context.deserialize(in, MerkleScheduledTransactionsState.CURRENT_VERSION);
            return context;
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataInputStream");
        }
    }

    @Override
    public void write(
            final @NonNull MerkleScheduledTransactionsState item, final @NonNull WritableSequentialData output)
            throws IOException {
        if (output instanceof SerializableDataOutputStream out) {
            item.serialize(out);
        } else {
            throw new IllegalArgumentException("Expected a SerializableDataOutputStream");
        }
    }

    @Override
    public int measure(@NonNull ReadableSequentialData input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean fastEquals(@NonNull MerkleScheduledTransactionsState item, @NonNull ReadableSequentialData input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int measureRecord(MerkleScheduledTransactionsState merkleScheduledTransactionsState) {
        return 0;
    }
}
