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

package com.swirlds.platform.bls.protocol;

/**
 * Thrown when a {@link BlsProtocol} experiences an error
 *
 * <p>This exception is used for errors that are *expected* in the course of a failed protocol, e.g.
 * from insufficient participation. If one protocol instance throws this exception, so too will all
 * other participating instances (if everything is working correctly).
 *
 * <p>Problems that arise unexpectedly in a particular protocol instance, therefore, shouldn't use
 * this exception. Instead, such errors should throw {@link RuntimeException} or {@link
 * IllegalStateException}, indicating a total breakdown in a node's ability to continue protocol
 * execution.
 */
public class BlsProtocolException extends RuntimeException {
    /**
     * String constructor
     *
     * @param message message describing the error that occurred
     */
    public BlsProtocolException(final String message) {
        super(message);
    }

    /**
     * String and throwable constructor
     *
     * @param message string describing the exception
     * @param cause the cause of the exception
     */
    public BlsProtocolException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Throwable constructor
     *
     * @param cause the cause of the exception
     */
    public BlsProtocolException(final Throwable cause) {
        super(cause);
    }
}
