/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.crypto.Hash;

/**
 * A record that contains a path and a hash. It serves for both node types, internal and leaf.
 * @param path the path of the node
 * @param hash the hash of the node
 */
public record VirtualHashRecord(long path, Hash hash) {

    public VirtualHashRecord(long path) {
        this(path, null);
    }

    public VirtualHashBytes toBytes() {
        return new VirtualHashBytes(path, hash.getBytes());
    }
}
