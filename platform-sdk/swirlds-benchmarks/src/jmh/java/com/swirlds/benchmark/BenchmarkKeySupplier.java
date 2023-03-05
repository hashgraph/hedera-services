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

package com.swirlds.benchmark;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

public class BenchmarkKeySupplier implements SelfSerializableSupplier<BenchmarkKey> {
    static final long CLASS_ID = 0x1828ec5a6d2L;
    static final int VERSION = 1;

    public BenchmarkKeySupplier() {
        // for serde
    }

    @Override
    public void deserialize(SerializableDataInputStream serializableDataInputStream, int i) {
        // not used
    }

    @Override
    public void serialize(SerializableDataOutputStream serializableDataOutputStream) {
        // not used
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public BenchmarkKey get() {
        return new BenchmarkKey();
    }
}
