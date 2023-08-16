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

import static org.apache.commons.lang3.RandomUtils.nextInt;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public final class ExampleFixedSizeVirtualValueSerializer
        implements SelfSerializableSupplier<ExampleFixedSizeVirtualValue> {

    public ExampleFixedSizeVirtualValueSerializer() {}

    private static final long CLASS_ID = 0x954027b17b5b54b0L;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ExampleFixedSizeVirtualValue.SERIALIZATION_VERSION;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        // no-op
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        // no-op
    }

    @Override
    public ExampleFixedSizeVirtualValue get() {
        return new ExampleFixedSizeVirtualValue(nextInt());
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object obj) {
        return obj instanceof ExampleFixedSizeVirtualValueSerializer;
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return (int) CLASS_ID;
    }
}
