/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.virtual;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public final class BlockInfo implements VirtualKey {

    static final long RUNTIME_CONSTRUCTABLE_ID = 0xb2c0a1f711abdabdL;
    static final int MERKLE_VERSION = 1;

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public void serialize(@NonNull SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(@NonNull SerializableDataInputStream in, int version) throws IOException {}

    @Override
    public int getMinimumSupportedVersion() {
        return VirtualKey.super.getMinimumSupportedVersion();
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }
}
