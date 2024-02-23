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

package com.swirlds.common.threading.pool;

import java.time.Instant;

/**
 * An exception thrown by @{@link ParallelExecutor} when one or both of the tasks fails. Since these tasks can fail at
 * different times, a timestamp is added to this exception.
 */
public class ParallelExecutionException extends Exception {
    /**
     * @param cause
     * 		the original exception
     * @param time
     * 		the time to attach to the message
     */
    public ParallelExecutionException(final Throwable cause, final Instant time) {
        super("Time thrown: " + time.toString(), cause);
    }

    /**
     * @param cause
     * 		the original exception
     */
    public ParallelExecutionException(final Throwable cause) {
        this(cause, Instant.now());
    }
}
