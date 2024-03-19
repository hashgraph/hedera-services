/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.validation;

import com.swirlds.common.wiring.component.InputWireLabel;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Validates that events are internally complete and consistent.
 */
public interface InternalEventValidator {
    /**
     * Validate the internal data integrity of an event.
     * <p>
     * If the event is determined to be valid, it is returned.
     *
     * @param event the event to validate
     * @return the event if it is valid, otherwise null
     */
    @InputWireLabel("non-validated events")
    @Nullable
    GossipEvent validateEvent(@NonNull GossipEvent event);
}
