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

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.logging.LogMarker;

/**
 * Thrown when the given {@link NodeId} was not found in the {@link AddressBook} or a {@code null} node id was passed by
 * the caller.
 */
public class InvalidNodeIdException extends PlatformException {

    public InvalidNodeIdException() {
        super(LogMarker.EXCEPTION);
    }

    public InvalidNodeIdException(final String message) {
        super(message, LogMarker.EXCEPTION);
    }

    public InvalidNodeIdException(final String message, final Throwable cause) {
        super(message, cause, LogMarker.EXCEPTION);
    }

    public InvalidNodeIdException(final Throwable cause) {
        super(cause, LogMarker.EXCEPTION);
    }
}
