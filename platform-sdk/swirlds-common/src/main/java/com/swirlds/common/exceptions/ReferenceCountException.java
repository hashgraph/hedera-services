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

package com.swirlds.common.exceptions;

/**
 * This exception is thrown when there are problems with reference counts.
 */
public class ReferenceCountException extends RuntimeException {

    public ReferenceCountException() {}

    public ReferenceCountException(final String message) {
        super(message);
    }

    public ReferenceCountException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public ReferenceCountException(final Throwable cause) {
        super(cause);
    }

    public ReferenceCountException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
