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

package com.hedera.node.app.tss.api;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A message sent as part of either genesis keying, or rekeying.
 * @param bytes the byte representation of the opaque underlying structure used by the library
 */
public record TssMessage(@NonNull byte[] bytes) {

    /**
     * Constructor
     * @param bytes bytes the byte representation of the opaque underlying structure used by the library
     */
    public TssMessage {
        requireNonNull(bytes, "bytes must not be null");
    }
}