/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging.legacy.payload;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * This payload is used to signal that a state written to disk did not collect sufficient signatures to be considered
 * complete.
 */
public class InsufficientSignaturesPayload extends AbstractLogPayload {

    /**
     * Constructor
     * @param message a human-readable message
     */
    public InsufficientSignaturesPayload(@NonNull final String message) {
        super(message);
    }
}
