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

package com.swirlds.virtualmap.serialize;

import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * An interface to serialize values used in virtual maps. Virtual values are serializable in themselves,
 * but the corresponding interface, {@link SelfSerializable}, lacks some abilities needed by virtual
 * data sources. For example, there is no way to easily get serialized value size in bytes, and there
 * are no methods to serialize / deserialize values to / from byte buffers.
 *
 * <p>Serialization bytes used by value serializers may or may not be identical to bytes used when values
 * are self-serialized. In many cases value serializers will just delegate serialization to values, just
 * returning the size of serialized byte array. On deserialization, typical implementation is to
 * create a new value object and call its {@link SelfSerializable#deserialize(SerializableDataInputStream, int)}
 * method.
 *
 * @param <V>
 *     Virtual value type
 */
public interface ValueSerializer<V extends VirtualValue> extends BaseSerializer<V>, SelfSerializable {

    @Override
    default void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        // most value serializers are stateless, so there is nothing to serialize
    }

    @Override
    default void deserialize(@NonNull final SerializableDataInputStream in, int version) throws IOException {
        // most value serializers are stateless, so there is nothing to deserialize
    }
}
