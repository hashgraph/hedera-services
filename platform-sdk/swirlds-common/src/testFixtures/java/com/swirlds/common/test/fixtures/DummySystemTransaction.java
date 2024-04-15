/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.fixtures;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.system.transaction.ConsensusTransactionImpl;
import java.io.IOException;

/**
 * Dummy instance of a system transaction, for testing purposes
 */
public class DummySystemTransaction extends ConsensusTransactionImpl {

    @Override
    public long getClassId() {
        return 0x4509c61070fdcc93L;
    }

    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {}

    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {}

    @Override
    public int getSerializedLength() {
        return 0;
    }

    @Override
    public int getVersion() {
        return 0;
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public boolean isSystem() {
        return true;
    }

    @Override
    public byte[] getContents() {
        return null;
    }
}
