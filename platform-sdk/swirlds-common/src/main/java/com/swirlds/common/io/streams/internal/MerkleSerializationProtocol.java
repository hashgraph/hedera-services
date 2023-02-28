/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.io.streams.internal;

/**
 * Version numbers for the merkle serialization protocol.
 */
public final class MerkleSerializationProtocol {

    /**
     * The original merkle serialization protocol.
     */
    public static final int VERSION_1_ORIGINAL = 1;

    /**
     * Added an object that is serialized to the stream that contains various stream configuration options.
     */
    public static final int VERSION_2_ADDED_OPTIONS = 2;

    /**
     * Removed serialization options, as they were no longer needed after serialization simplifications.
     */
    public static final int VERSION_3_REMOVED_OPTIONS = 3;

    /**
     * The current protocol version.
     */
    public static final int CURRENT = VERSION_3_REMOVED_OPTIONS;

    private MerkleSerializationProtocol() {}
}
