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

package com.swirlds.common.notification;

/**
 * The base functional interface that must be implemented by all notification listeners. Uses the default {@link
 * DispatchModel} configuration.
 *
 * @param <N>
 * 		the type of the supported {@link Notification} which is passed to the {@link #notify(Notification)} method.
 */
@FunctionalInterface
@DispatchModel
public interface Listener<N extends Notification> {

    /**
     * Called for each {@link Notification} that this listener should handle.
     *
     * @param data
     * 		the notification to be handled
     */
    void notify(final N data);
}
