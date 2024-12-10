/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.config.types;

/**
 * Enumerates the choices of what stream to produce; currently only records or both records and blocks are supported,
 * because record queries are not yet supported by translating from blocks.
 */
public enum StreamMode {
    /**
     * Stream records only.
     */
    RECORDS,
    /**
     * Stream blocks only.
     */
    BLOCKS,
    /**
     * Stream both blocks and records.
     */
    BOTH
}
