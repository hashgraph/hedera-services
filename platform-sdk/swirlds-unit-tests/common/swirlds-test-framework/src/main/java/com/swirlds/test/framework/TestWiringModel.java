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

package com.swirlds.test.framework;

import com.swirlds.base.time.Time;
import com.swirlds.common.wiring.OutputWire;
import com.swirlds.common.wiring.SolderType;
import com.swirlds.common.wiring.WiringModel;
import com.swirlds.common.wiring.utility.ModelGroup;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * A simple version of a wiring model for scenarios where the wiring model is not needed.
 */
public class TestWiringModel extends WiringModel {

    private static final TestWiringModel INSTANCE = new TestWiringModel();

    /**
     * Get the singleton instance.
     *
     * @return the singleton instance
     */
    @NonNull
    public static TestWiringModel getInstance() {
        return INSTANCE;
    }

    /**
     * Constructor.
     */
    private TestWiringModel() {
        super(TestPlatformContextBuilder.create().build(), Time.getCurrent());
    }

    /**
     * Unsupported.
     */
    @NonNull
    @Override
    public OutputWire<Instant> buildHeartbeatWire(@NonNull final Duration period) {
        throw new UnsupportedOperationException("TestWiringModel does not support heartbeats");
    }

    /**
     * Unsupported.
     */
    @Override
    public OutputWire<Instant> buildHeartbeatWire(final double frequency) {
        throw new UnsupportedOperationException("TestWiringModel does not support heartbeats");
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
    public String generateWiringDiagram(@NonNull final Set<ModelGroup> modelGroups) {
        return "do it yourself";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerVertex(@NonNull final String vertexName, final boolean insertionIsBlocking) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEdge(
            @NonNull final String originVertex,
            @NonNull final String destinationVertex,
            @NonNull final String label,
            @NonNull final SolderType solderType) {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop() {}
}
