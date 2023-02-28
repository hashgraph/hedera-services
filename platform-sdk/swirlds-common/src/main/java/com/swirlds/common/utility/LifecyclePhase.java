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

package com.swirlds.common.utility;

/**
 * Phases in an object's {@link Lifecycle}.
 */
public enum LifecyclePhase {
    /**
     * The object's constructor has been called, but {@link Lifecycle#start()} has not yet been called.
     */
    NOT_STARTED,
    /**
     * {@link Lifecycle#start()} has been called and has not yet finished. This is an optional phase, some objects
     * may jump straight to {@link #STARTED}.
     */
    STARTING,
    /**
     * {@link Lifecycle#start()} has been called and has been completed.
     */
    STARTED,
    /**
     * {@link Lifecycle#stop()} has been called but has not yet finished. This is an optional phase, some objects
     * may jump straight to {@link #STOPPED}.
     */
    STOPPING,
    /**
     * {@link Lifecycle#stop()} has been called and has completed.
     */
    STOPPED,
    /**
     * This object has encountered an unrecoverable error and is broken.
     */
    ERROR
}
