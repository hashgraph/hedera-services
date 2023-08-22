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

package com.swirlds.logging.v2.internal;

import com.swirlds.logging.v2.Marker;
import java.util.Optional;

public class MarkerImpl implements Marker {

    private final String name;

    private final Marker parent;

    public MarkerImpl(String name, Marker parent) {
        this.name = name;
        this.parent = parent;
    }

    public MarkerImpl(String name) {
        this(name, null);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Optional<Marker> getParent() {
        return Optional.ofNullable(parent);
    }
}
