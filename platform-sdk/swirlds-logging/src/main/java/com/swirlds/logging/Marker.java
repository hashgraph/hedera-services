/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.logging;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A marker is a named reference to a location / package / context in the code. It can be used to filter log messages at
 * runtime
 *
 * @param name   the name of the marker
 * @param parent the parent marker (if present)
 */
public record Marker(@NonNull String name, @Nullable Marker parent) {

    public Marker {
        Objects.requireNonNull(name, "name must not be null");
    }

    public Marker(@NonNull final String name) {
        this(name, null);
    }
}
