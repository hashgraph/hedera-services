/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.sync;

import com.swirlds.platform.Connection;

/**
 * Thrown if any issue occurs during a sync that is not connection related
 */
public class SyncException extends Exception {
    public SyncException(final Connection connection, final String message, final Throwable cause) {
        super(connection.getDescription() + " " + message, cause);
    }

    public SyncException(final Connection connection, final String message) {
        this(connection, message, null);
    }

    public SyncException(final String message) {
        super(message);
    }
}
