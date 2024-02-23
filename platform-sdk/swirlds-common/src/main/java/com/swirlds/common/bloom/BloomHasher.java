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

package com.swirlds.common.bloom;

import com.swirlds.common.io.SelfSerializable;

/**
 * An object that hashes elements for the bloom filter.
 *
 * @param <T>
 * 		the type of the object in the bloom filter
 */
public interface BloomHasher<T> extends SelfSerializable {

    /**
     * Hash an element for the bloom filter
     *
     * @param element
     * 		the element to hash. Null values may be optionally supported.
     * @param maxHash
     * 		the maximum permitted value of a hash
     * @param hashes
     * 		an array into which the hashes must be written. The number of hashes
     * 		computed must equal the length of the array.
     * @throws NullPointerException
     * 		if this implementation does not support null elements but a null
     * 		element is provided
     */
    void hash(T element, long maxHash, long[] hashes);
}
