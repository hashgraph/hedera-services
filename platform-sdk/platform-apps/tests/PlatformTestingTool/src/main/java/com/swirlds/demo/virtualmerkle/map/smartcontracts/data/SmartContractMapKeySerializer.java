/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.data;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.virtualmap.serialize.KeySerializer;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This class is the serializer for {@link SmartContractMapKey}.
 */
public final class SmartContractMapKeySerializer implements KeySerializer<SmartContractMapKey> {

    private static final long CLASS_ID = 0x2d68463768cf4c5AL;

    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return SmartContractMapKey.getSizeInBytes();
    }

    @Override
    public void serialize(@NonNull final SmartContractMapKey key, @NonNull final WritableSequentialData out) {
        key.serialize(out);
    }

    @Override
    public SmartContractMapKey deserialize(@NonNull final ReadableSequentialData in) {
        final SmartContractMapKey key = new SmartContractMapKey();
        key.deserialize(in);
        return key;
    }

    @Override
    public boolean equals(@NonNull final BufferedData buffer, @NonNull final SmartContractMapKey keyToCompare) {
        return keyToCompare.equals(buffer);
    }
}
