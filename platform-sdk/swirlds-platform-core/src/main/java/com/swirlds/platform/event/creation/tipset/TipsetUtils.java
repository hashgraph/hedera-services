/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.creation.tipset;

import com.swirlds.platform.system.events.BaseEventHashedData;
import com.swirlds.platform.system.events.EventDescriptor;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;

/**
 * Misc tipset utilities.
 */
public final class TipsetUtils {

    private TipsetUtils() {}

    /**
     * Get the descriptors of an event's parents.
     *
     * @param event the event to the parent descriptors for
     * @return a list of parent descriptors
     */
    @NonNull
    public static List<EventDescriptor> getParentDescriptors(@NonNull final BaseEventHashedData event) {
        final List<EventDescriptor> parentDescriptors = new ArrayList<>(2);

        if (event.hasSelfParent()) {
            parentDescriptors.add(event.getSelfParent());
        }
        if (event.hasOtherParent()) {
            event.getOtherParents().forEach(parentDescriptors::add);
        }

        return parentDescriptors;
    }
}
