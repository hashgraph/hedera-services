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

package com.swirlds.state.merkle.vmapsupport;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

public class SingleLongKey implements VirtualKey {

    // Class ID
    private static final long CLASS_ID = 0x80bada5ba78a211cL;

    // Class version
    private static final class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    private long value;

    // Required for deserialization
    public SingleLongKey() {}

    public SingleLongKey(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
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
    public void serialize(@NonNull SerializableDataOutputStream out) throws IOException {
        out.writeLong(value);
    }

    @Override
    public void deserialize(@NonNull SerializableDataInputStream in, int version) throws IOException {
        value = in.readLong();
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public boolean equals(final Object obj) {
        return (obj instanceof SingleLongKey that) && (value == that.value);
    }
}
