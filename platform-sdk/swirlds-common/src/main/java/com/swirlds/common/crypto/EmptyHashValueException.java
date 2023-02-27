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

package com.swirlds.common.crypto;

import com.swirlds.logging.LogMarker;

/**
 * Exception caused when provided hash value contains all zeros
 */
public class EmptyHashValueException extends CryptographyException {

    private static final String MESSAGE =
            "Provided hash value contained all zeros, hashes must contain at least one " + "non-zero byte";

    public EmptyHashValueException() {
        this(MESSAGE);
    }

    public EmptyHashValueException(final String message) {
        super(message, LogMarker.TESTING_EXCEPTIONS);
    }

    public EmptyHashValueException(final String message, final Throwable cause) {
        super(message, cause, LogMarker.TESTING_EXCEPTIONS);
    }

    public EmptyHashValueException(final Throwable cause) {
        this(MESSAGE, cause);
    }
}
