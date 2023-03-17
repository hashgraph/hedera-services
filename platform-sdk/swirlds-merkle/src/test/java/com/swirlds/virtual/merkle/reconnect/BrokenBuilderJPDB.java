/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualDataSourceBuilder;
import java.nio.file.Path;

public class BrokenBuilderJPDB extends BrokenBuilder {

    private static final long CLASS_ID = 0x5a79654cd0f96dcfL;

    public BrokenBuilderJPDB() {}

    public BrokenBuilderJPDB(VirtualDataSourceBuilder<TestKey, TestValue> delegate) {
        super(delegate);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public BreakableDataSource build(final String label, final boolean withDbCompactionEnabled) {
        return new BreakableDataSourceJPDB(this, delegate.build(label, withDbCompactionEnabled));
    }

    @Override
    public BreakableDataSource copy(final VirtualDataSource<TestKey, TestValue> snapshotMe,
            final boolean makeCopyActive) {
        final var breakableSnapshot = (BreakableDataSource) snapshotMe;
        return new BreakableDataSourceJPDB(this, delegate.copy(breakableSnapshot.delegate, makeCopyActive));
    }

    @Override
    public BreakableDataSource restore(final String label, final Path from) {
        return new BreakableDataSourceJPDB(this, delegate.restore(label, from));
    }
}
