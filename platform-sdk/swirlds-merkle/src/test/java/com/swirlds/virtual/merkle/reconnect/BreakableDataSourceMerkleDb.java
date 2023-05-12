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

import static com.swirlds.common.utility.Units.BYTES_TO_BITS;
import static com.swirlds.common.utility.Units.MEBIBYTES_TO_BYTES;

import com.swirlds.merkledb.files.hashmap.HalfDiskVirtualKeySet;
import com.swirlds.virtual.merkle.TestKey;
import com.swirlds.virtual.merkle.TestKeySerializerMerkleDb;
import com.swirlds.virtual.merkle.TestValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualKeySet;

public class BreakableDataSourceMerkleDb extends BreakableDataSource {

    public BreakableDataSourceMerkleDb(
            final BrokenBuilder builder, final VirtualDataSource<TestKey, TestValue> delegate) {
        super(builder, delegate);
    }

    @Override
    public VirtualKeySet<TestKey> buildKeySet() {
        return new HalfDiskVirtualKeySet<>(
                new TestKeySerializerMerkleDb(), 10, 2L * MEBIBYTES_TO_BYTES * BYTES_TO_BITS, 1_000_000, 10_000);
    }
}
