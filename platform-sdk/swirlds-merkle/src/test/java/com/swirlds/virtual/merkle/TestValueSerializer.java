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

package com.swirlds.virtual.merkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;
import java.io.IOException;

public class TestValueSerializer implements SelfSerializableSupplier<TestValue> {
    @Override
    public long getClassId() {
        return 53543453;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {}

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public TestValue get() {
        return new TestValue();
    }
}
