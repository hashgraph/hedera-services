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

package com.swirlds.logging.json;

import com.swirlds.logging.LogMarker;
import com.swirlds.logging.LogMarkerType;
import java.util.function.Predicate;

/**
 * Check if the log marker signifies an error. Allows all entries with a {@link LogMarkerType#ERROR} type to pass.
 */
public class HasErrorMarkerFilter implements Predicate<JsonLogEntry> {

    public static HasErrorMarkerFilter hasErrorMarker() {
        return new HasErrorMarkerFilter();
    }

    public HasErrorMarkerFilter() {}

    @Override
    public boolean test(JsonLogEntry jsonLogEntry) {
        String markerName = jsonLogEntry.getMarker();
        try {
            return LogMarker.valueOf(markerName).getType().equals(LogMarkerType.ERROR);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
