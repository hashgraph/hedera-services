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

package com.swirlds.jasperdb;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

@SuppressWarnings("ResultOfMethodCallIgnored")
public final class ExampleVariableSizeVirtualValueSerializer
        implements SelfSerializableSupplier<ExampleVariableSizeVirtualValue> {
    public ExampleVariableSizeVirtualValueSerializer() {}

    private static final long CLASS_ID = 0x3f501a6ed395e07fL;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ExampleVariableSizeVirtualValue.SERIALIZATION_VERSION;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {}

    @Override
    public ExampleVariableSizeVirtualValue get() {
        return new ExampleVariableSizeVirtualValue();
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ExampleVariableSizeVirtualValueSerializer;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CLASS_ID;
    }
}
