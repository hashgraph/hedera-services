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

package com.swirlds.platform.state.signed;

/**
 * This exception is thrown if a signed state is invalid, usually due to lack of sufficient signatures.
 */
public class SignedStateInvalidException extends RuntimeException {

    public SignedStateInvalidException(final String message) {
        super(message);
    }

    public SignedStateInvalidException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public SignedStateInvalidException(final Throwable cause) {
        super(cause);
    }
}
