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

package com.swirlds.common.io;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

/**
 * A SerializableDet that knows how to serialize and deserialize itself.
 */
public interface SelfSerializable extends SerializableDet, FunctionalSerialize {

    /**
     * Deserializes an instance that has been previously serialized by {@link FunctionalSerialize#serialize(SerializableDataOutputStream)}.
     * This method should support all versions of the serialized data.
     *
     * @param in
     * 		The stream to read from.
     * @param version
     * 		The version of the serialized instance. Guaranteed to be greater or equal to the minimum version
     * 		and less than or equal to the current version.
     * @throws IOException
     * 		Thrown in case of an IO exception.
     */
    void deserialize(SerializableDataInputStream in, int version) throws IOException;
}
