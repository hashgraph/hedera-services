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

package com.hedera.node.app.tss.cryptography.tss.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An exception thrown in the context of reading a {@link TssMessage} from its byte array representation.
 * @see TssMessage#toBytes()
 */
public class TssMessageParsingException extends Exception {

    /**
     * Exception with the specified detail message and cause.
     * @param message the detail message (which is saved for later retrieval by the getMessage() method).
     * @param cause the cause (which is saved for later retrieval by the getCause() method).
     * (A null value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public TssMessageParsingException(@NonNull final String message, @NonNull final Throwable cause) {
        super(message, cause);
    }
}
