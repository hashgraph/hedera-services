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

package com.swirlds.common.test;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.system.transaction.SystemTransactionType;
import com.swirlds.common.system.transaction.internal.SystemTransaction;
import java.io.IOException;

/**
 * Dummy instance of a system transaction, for testing purposes
 */
public class DummySystemTransaction extends SystemTransaction {

    @Override
    public long getClassId() {
        return 0;
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
    public SystemTransactionType getType() {
        return null;
    }
}
