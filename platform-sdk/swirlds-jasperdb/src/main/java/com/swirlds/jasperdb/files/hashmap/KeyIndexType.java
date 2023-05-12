/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.jasperdb.files.hashmap;

/**
 * The type of index that the database should use for a key.
 */
public enum KeyIndexType {
    /**
     * Index that can handle any key type and uses disk. See {@link com.swirlds.jasperdb.files.hashmap.HalfDiskHashMap}
     */
    GENERIC,
    /**
     * Index that assumes the keys are sequential longs without any gaps and implement {@link com.swirlds.virtualmap.VirtualLongKey}.
     * This index is 100% in memory, so use with care.
     */
    SEQUENTIAL_INCREMENTING_LONGS
}
