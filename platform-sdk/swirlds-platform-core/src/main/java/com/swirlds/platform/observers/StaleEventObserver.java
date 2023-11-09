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

package com.swirlds.platform.observers;

import com.swirlds.platform.internal.EventImpl;

/**
 * An observer that is notified when an event becomes stale.
 *
 * Stale events do not reach consensus, and their transactions are never handled. The agreement is guaranteed: if
 * one computer decides that an event is stale, then any other computer that has that event will eventually agree
 * that it’s stale.
 * And some computers might never receive the event at all. If one computer decides an event is “consensus”, then
 * any other computer with that event will eventually decide that it is consensus. And every computer will either
 * receive that event or will do a reconnect where it receives a state that includes the effects of handling that
 * event.
 */
@FunctionalInterface
public interface StaleEventObserver extends EventObserver {

    /**
     * Announce that the given event is now stale
     * @param event
     * 		the event
     */
    void staleEvent(EventImpl event);
}
