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

package com.hedera.node.app.records.impl.producers;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Creates a new {@link BlockStreamWriter} instance on demand, based on configuration. During processing of
 * transactions, when we determine it is time to write a new block to file, then we need to create a new
 * {@link BlockStreamWriter} instance. This factory is used to create that instance. Creation of the instance may fail,
 * for example, if the filesystem is full or the network destination is unavailable. Callers must be prepared to deal
 * with these failures.
 */
public interface BlockStreamWriterFactory {
    /**
     * Create a new {@link BlockStreamWriter} instance.
     *
     * @return the new instance
     * @throws RuntimeException if creation fails
     */
    @NonNull
    BlockStreamWriter create() throws RuntimeException;
}
