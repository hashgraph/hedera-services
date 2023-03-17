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

package com.swirlds.common.notification;

/**
 * Defines how the dispatcher for a given {@link Listener} operates with respect to the caller.
 */
public enum DispatchMode {
    /**
     * Blocking mode which guarantees that the {@link Notification} will have been successfully dispatched to all
     * registered {@link Listener} implementations before returning.
     *
     * The only guarantees provided are that the caller will be blocked until all registered listeners have been
     * notified and that any exceptions thrown by a listener implementation will be propagated to the caller.
     */
    SYNC,

    /**
     * Queues the notification for delivery and returns control to the caller as quickly as possible.
     * Any exceptions thrown will be available via the {@link NotificationResult#getExceptions()} method.
     */
    ASYNC
}
