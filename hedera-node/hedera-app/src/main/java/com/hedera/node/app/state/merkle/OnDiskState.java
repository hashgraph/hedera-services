/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.WritableState;
import com.hedera.node.app.state.MutableStateBase;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

/**
 * An implementation of {@link WritableState} backed by a {@link VirtualMap}, resulting in a state
 * that is stored on disk.
 *
 * @param <K> The type of key for the state
 * @param <V> The type of value for the state
 */
public final class OnDiskState<K, V> extends MutableStateBase<K, V> {
    private final VirtualMap<OnDiskKey, OnDiskValue> virtualMap;

    public OnDiskState(
            @NonNull final String stateKey,
            @NonNull final VirtualMap<OnDiskKey, OnDiskValue> virtualMap) {
        super(stateKey);
        this.virtualMap = Objects.requireNonNull(virtualMap);
    }

    @Override
    protected V readFromDataSource(@NonNull K key) {
        throw new NotImplementedException("Gotta do this");
        //        return virtualMap.get(key);
    }

    @Override
    protected V getForModifyFromDataSource(@NonNull K key) {
        throw new NotImplementedException("Gotta do this");
        //        return virtualMap.getForModify(key);
    }

    @Override
    protected void putIntoDataSource(@NonNull K key, @NonNull V value) {
        throw new NotImplementedException("Gotta do this");
        //        virtualMap.put(key, value);
    }

    @Override
    protected void removeFromDataSource(@NonNull K key) {
        throw new NotImplementedException("Gotta do this");
        //        virtualMap.remove(key);
    }

    private static final class OnDiskKey implements VirtualKey<OnDiskKey> {

        @Override
        public int hashCode() {
            return 0;
        }

        @Override
        public void serialize(ByteBuffer byteBuffer) throws IOException {}

        @Override
        public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {}

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i)
                throws IOException {}

        @Override
        public long getClassId() {
            return 0;
        }

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream)
                throws IOException {}

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public int compareTo(OnDiskKey o) {
            return 0;
        }
    }

    private static final class OnDiskValue implements VirtualValue {

        @Override
        public VirtualValue copy() {
            return null;
        }

        @Override
        public VirtualValue asReadOnly() {
            return null;
        }

        @Override
        public void serialize(ByteBuffer byteBuffer) throws IOException {}

        @Override
        public void deserialize(ByteBuffer byteBuffer, int i) throws IOException {}

        @Override
        public void deserialize(SerializableDataInputStream serializableDataInputStream, int i)
                throws IOException {}

        @Override
        public void serialize(SerializableDataOutputStream serializableDataOutputStream)
                throws IOException {}

        @Override
        public long getClassId() {
            return 0;
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }
}
