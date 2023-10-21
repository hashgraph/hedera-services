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

package com.swirlds.common.wiring.model;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.WiringModel;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.Set;

/**
 * A standard implementation of a wiring model.
 */
public class StandardWiringModel extends WiringModel {

    private final Set<String> vertices = new HashSet<>();

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param time            provides wall clock time
     */
    public StandardWiringModel(@NonNull final PlatformContext platformContext, @NonNull final Time time) {
        super(platformContext, time);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean checkForCyclicalBackpressure() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String generateWiringDiagram() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void registerVertex(@NonNull final String vertexName) {
        final boolean unique = vertices.add(vertexName);
        if (!unique) {
            throw new IllegalArgumentException("Duplicate vertex name: " + vertexName);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void registerEdge(
            @NonNull final String originVertex, @NonNull final String destinationVertex, @NonNull final String label) {}
}
