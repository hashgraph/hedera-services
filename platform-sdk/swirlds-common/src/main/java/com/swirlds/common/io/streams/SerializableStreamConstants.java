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

package com.swirlds.common.io.streams;

import com.swirlds.common.io.SelfSerializable;

public final class SerializableStreamConstants {

    private SerializableStreamConstants() {}

    /** The value of the length of an array/list will when the array/list is null */
    public static final int NULL_LIST_ARRAY_LENGTH = -1;
    /** The class ID of a {@link SelfSerializable} instance when the instance is null */
    public static final long NULL_CLASS_ID = Long.MIN_VALUE;
    /** The version of a {@link SelfSerializable} instance when the instance is null */
    public static final int NULL_VERSION = Integer.MIN_VALUE;
    /** The value of Instant.epochSecond when instant is null */
    public static final long NULL_INSTANT_EPOCH_SECOND = Long.MIN_VALUE;

    /** number of bytes used by a boolean variable during serialization */
    public static final int BOOLEAN_BYTES = Byte.BYTES;
    /** number of bytes used by a class ID variable during serialization */
    public static final int CLASS_ID_BYTES = Long.BYTES;
    /** number of bytes used by a version variable during serialization */
    public static final int VERSION_BYTES = Integer.BYTES;

    /**
     * The current version of the serialization protocol implemented by {@link SerializableDataOutputStream} and
     * {@link SerializableDataInputStream}
     */
    public static final int SERIALIZATION_PROTOCOL_VERSION = 1;

    /**
     * Should stream methods that use checksums use checksums by default?
     */
    public static final boolean DEFAULT_CHECKSUM = false;
}
