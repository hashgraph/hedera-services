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

package com.swirlds.platform.system.events;

import static com.swirlds.platform.system.events.EventConstants.GENERATION_UNDEFINED;

import com.hedera.hapi.platform.event.EventCore;
import com.hedera.hapi.platform.event.EventDescriptor;
import com.swirlds.platform.event.AncientMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public final class EventCoreUtils {
    private EventCoreUtils() {}

    /**
     * Get the generation of the event core. The generation is the maximum generation of the parents plus one.
     * If the event core has no parents, the generation is 0.
     *
     * @param eventCore the event core
     *
     * @return the generation of the event core
     *
     * @see EventConstants#GENERATION_UNDEFINED
     */
    public static long getGeneration(@NonNull final EventCore eventCore) {
        Objects.requireNonNull(eventCore, "eventCore must not be null");

        return 1
                + eventCore.parents().stream()
                        .mapToLong(EventDescriptor::generation)
                        .max()
                        .orElse(GENERATION_UNDEFINED);
    }

    /**
     * Get the ancient indicator of the event core. The ancient indicator is the generation of the event core.
     *
     * @param eventCore the event core
     * @param ancientMode the ancient mode
     *
     * @return the ancient indicator of the event core
     *
     * @see EventCoreUtils#getGeneration(EventCore)
     */
    public static long getAncientIndicator(@NonNull final EventCore eventCore, @NonNull final AncientMode ancientMode) {
        Objects.requireNonNull(eventCore, "eventCore must not be null");
        Objects.requireNonNull(ancientMode, "ancientMode must not be null");

        return switch (ancientMode) {
            case GENERATION_THRESHOLD -> getGeneration(eventCore);
            case BIRTH_ROUND_THRESHOLD -> eventCore.birthRound();
        };
    }
}
