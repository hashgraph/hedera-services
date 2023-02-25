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

import com.swirlds.platform.event.GossipEvent;

/**
 * An observer that is notified when an event is received, deduplicated and validated, but before it is added to
 * consensus. We might not have the parents of this event, so it might be added to consensus later, or never.
 */
@FunctionalInterface
public interface EventReceivedObserver {
    /**
     * The given event has been received, deduplicated and validated
     *
     * @param event
     * 		the event
     */
    void receivedEvent(GossipEvent event);
}
