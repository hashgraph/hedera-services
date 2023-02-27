/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.virtual.merkle.reconnect;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.io.IOException;
import java.nio.file.Path;

public abstract class BrokenBuilder implements VirtualDataSourceBuilder<TestKey, TestValue> {

    protected VirtualDataSourceBuilder<TestKey, TestValue> delegate;

    int numCallsBeforeThrow = Integer.MAX_VALUE;
    int numCalls = 0;
    int numTimesToBreak = 0;
    int numTimesBroken = 0;

    public BrokenBuilder() {}

    public BrokenBuilder(VirtualDataSourceBuilder<TestKey, TestValue> delegate) {
        this.delegate = delegate;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        delegate.serialize(out);
        out.writeInt(numCallsBeforeThrow);
        out.writeInt(numTimesToBreak);
        out.writeInt(numCalls);
        out.writeInt(numTimesBroken);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        delegate.deserialize(in, version);
        numCallsBeforeThrow = in.readInt();
        numTimesToBreak = in.readInt();
        numCalls = in.readInt();
        numTimesBroken = in.readInt();
    }

    @Override
    public abstract BreakableDataSource build(final String label, final boolean withDbCompactionEnabled);

    @Override
    public abstract BreakableDataSource copy(final VirtualDataSource<TestKey, TestValue> snapshotMe);

    @Override
    public void snapshot(final Path to, final VirtualDataSource<TestKey, TestValue> snapshotMe) {
        final var breakableSnapshot = (BreakableDataSource) snapshotMe;
        delegate.snapshot(to, breakableSnapshot.delegate);
    }

    @Override
    public abstract BreakableDataSource restore(final String label, final Path from);

    public void setNumCallsBeforeThrow(final int numCallsBeforeThrow) {
        this.numCallsBeforeThrow = numCallsBeforeThrow;
    }

    public void setNumTimesToBreak(final int numTimesToBreak) {
        this.numTimesToBreak = numTimesToBreak;
    }
}
