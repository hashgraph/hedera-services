/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.signing;

import com.swirlds.component.framework.component.InputWireLabel;
import com.swirlds.platform.event.PlatformEvent;
import com.swirlds.platform.system.events.UnsignedEvent;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Signs self events.
 */
public interface SelfEventSigner {

    /**
     * Signs an event and then returns it.
     *
     * @param event the event to sign
     * @return the signed event
     */
    @InputWireLabel("self events")
    @NonNull
    PlatformEvent signEvent(@NonNull UnsignedEvent event);
}
