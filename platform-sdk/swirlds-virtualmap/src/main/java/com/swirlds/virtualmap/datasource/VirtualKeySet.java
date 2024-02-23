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

package com.swirlds.virtualmap.datasource;

import com.swirlds.virtualmap.VirtualKey;
import java.io.Closeable;

/**
 * A set-like data structure for virtual map keys. Keys can be added but never removed.
 *
 * @param <K>
 * 		the type of the key
 */
public interface VirtualKeySet<K extends VirtualKey> extends Closeable {

    /**
     * Add a key to the set.
     *
     * @param key
     * 		the key to add
     */
    void add(K key);

    /**
     * Check if a key is contained within the set.
     *
     * @param key
     * 		the key in question
     * @return true if the key is contained in the set
     */
    boolean contains(K key);

    /**
     * {@inheritDoc}
     */
    void close();
}
