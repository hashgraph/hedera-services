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

package com.swirlds.demo.virtualmerkle.map.smartcontracts.bytecode;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.virtualmap.serialize.ValueSerializer;
import edu.umd.cs.findbugs.annotations.NonNull;

/** This class is the serializer of {@link SmartContractByteCodeMapValue}. */
public final class SmartContractByteCodeMapValueSerializer implements ValueSerializer<SmartContractByteCodeMapValue> {

    // Serializer class ID
    private static final long CLASS_ID = 0xd0030308e71e01adL;

    // Serializer version
    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Value data version
    private static final int DATA_VERSION = 1;

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getClassVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public long getCurrentDataVersion() {
        return DATA_VERSION;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return 1024; // guesstimation
    }

    @Override
    public int getSerializedSize(@NonNull final SmartContractByteCodeMapValue value) {
        return value.getSizeInBytes();
    }

    @Override
    public void serialize(
            @NonNull final SmartContractByteCodeMapValue value, @NonNull final WritableSequentialData out) {
        value.serialize(out);
    }

    @Override
    public SmartContractByteCodeMapValue deserialize(@NonNull final ReadableSequentialData in) {
        final SmartContractByteCodeMapValue value = new SmartContractByteCodeMapValue();
        value.deserialize(in);
        return value;
    }
}
