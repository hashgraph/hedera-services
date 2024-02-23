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

package com.swirlds.common.stream;

/**
 * Describes an object that requires specific alignment within a stream.
 * Linguistically similar to, but in no way actually related to "streamlined".
 */
public interface StreamAligned {

    long NO_ALIGNMENT = Long.MIN_VALUE;

    /**
     * Gets the stream alignment descriptor for the object, or {@link #NO_ALIGNMENT} if
     * this object does not care about its stream alignment. If two or more sequential
     * objects have the same stream alignment (excluding {@link #NO_ALIGNMENT})
     * then those objects are grouped together.
     *
     * @return the stream alignment of the object
     */
    default long getStreamAlignment() {
        return NO_ALIGNMENT;
    }
}
