/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.jasperdb.SelfSerializableSupplier;

public class UniqueTokenKeySupplier implements SelfSerializableSupplier<UniqueTokenKey> {
    static final long CLASS_ID = 0x8232d5e6ed77cc5cL;
    static final int CURRENT_VERSION = 1;

    @Override
    public void deserialize(
            final SerializableDataInputStream serializableDataInputStream, final int i) {
        /* No operations since no state needs to be restored. */
    }

    @Override
    public void serialize(final SerializableDataOutputStream serializableDataOutputStream) {
        /* No operations since no state needs to be saved. */
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public UniqueTokenKey get() {
        return new UniqueTokenKey();
    }
}
