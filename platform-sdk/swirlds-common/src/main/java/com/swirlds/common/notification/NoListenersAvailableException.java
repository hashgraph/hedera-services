/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.notification;

public class NoListenersAvailableException extends DispatchException {

    private static final String DEFAULT_MESSAGE = "Unable to dispatch when no listeners have been registered";

    public NoListenersAvailableException() {
        super(DEFAULT_MESSAGE);
    }

    public NoListenersAvailableException(final String message) {
        super(message);
    }

    public NoListenersAvailableException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public NoListenersAvailableException(final Throwable cause) {
        super(cause);
    }
}
