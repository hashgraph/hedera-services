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

package com.swirlds.common.merkle.route;

/**
 * Thrown when there is trouble parsing a merkle route.
 */
public class MerkleRouteFormatException extends RuntimeException {
    public MerkleRouteFormatException() {
        super();
    }

    public MerkleRouteFormatException(final String message) {
        super(message);
    }

    public MerkleRouteFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public MerkleRouteFormatException(final Throwable cause) {
        super(cause);
    }

    protected MerkleRouteFormatException(
            final String message,
            final Throwable cause,
            final boolean enableSuppression,
            final boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
