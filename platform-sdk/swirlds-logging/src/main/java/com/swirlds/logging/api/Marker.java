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

package com.swirlds.logging.api;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * A marker is a named reference to a location / package / context in the code. It can be used to filter log messages at
 * runtime
 *
 * @param name   the name of the marker
 * @param previous the previous marker (if present)
 */
public record Marker(@NonNull String name, @Nullable Marker previous) {

    public Marker {
        Objects.requireNonNull(name, "name must not be null");
    }

    public Marker(@NonNull final String name) {
        this(name, null);
    }

    @NonNull
    public List<String> getAllMarkerNames() {
        if (previous != null) {
            final List<String> result = new ArrayList<>(previous.getAllMarkerNames());
            result.add(name);
            return Collections.unmodifiableList(result);
        } else {
            return List.of(name);
        }
    }
}
