/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.status;

import com.swirlds.common.PlatformStatus;
import com.swirlds.common.PlatformStatusAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An exception thrown when an illegal {@link PlatformStatusAction} is received
 */
public class IllegalPlatformStatusException extends RuntimeException {
    /**
     * Constructor
     *
     * @param illegalAction the illegal action that was received
     * @param status        the status of the platform when the illegal action was received
     */
    public IllegalPlatformStatusException(
            @NonNull final PlatformStatusAction illegalAction, @NonNull final PlatformStatus status) {

        super("Received unexpected status action `%s` with current status of `%s`"
                .formatted(illegalAction.getClass().getSimpleName(), status.name()));
    }

    /**
     * String constructor
     *
     * @param message the message
     */
    public IllegalPlatformStatusException(@NonNull final String message) {
        super(message);
    }
}
