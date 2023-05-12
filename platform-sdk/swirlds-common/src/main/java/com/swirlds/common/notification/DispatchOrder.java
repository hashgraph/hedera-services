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
 * Defines how the dispatcher handles the delivery of {@link Notification} to each registered {@link Listener}
 * implementation.
 */
public enum DispatchOrder {
    /**
     * Provides no guarantees in terms of ordering when the dispatcher is called from multiple threads for the same
     * {@link Listener} class.
     *
     * If used with {@link DispatchMode#SYNC}, then all {@link Notification} dispatched from a single thread will be in
     * order.
     */
    UNORDERED,

    /**
     * Provides a best effort ordering guarantee that {@link Listener} implementations will be notified in the original
     * order the {@link Notification} were dispatched.
     */
    ORDERED
}
