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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Wiring for the {@link com.swirlds.platform.system.events.BirthRoundMigrationShim}.
 *
 * @param eventInput  the input wire for events to be migrated
 * @param eventOutput the output wire for migrated events
 */
public record BirthRoundMigrationShimWiring(
        @NonNull InputWire<GossipEvent> eventInput, @NonNull OutputWire<GossipEvent> eventOutput) {

    /**
     * Create a new instance of this wiring.
     *
     * @param model the wiring model
     * @return the new wiring instance
     */
    public static BirthRoundMigrationShimWiring create(@NonNull final WiringModel model) {

        final TaskScheduler<GossipEvent> scheduler = model.schedulerBuilder("birthRoundMigrationShim")
                .withType(TaskSchedulerType.DIRECT_THREADSAFE)
                .build()
                .cast();

        return new BirthRoundMigrationShimWiring(
                scheduler.buildInputWire("un-migrated events"), scheduler.getOutputWire());
    }

    /**
     * Bind a birth round migration shim to this wiring.
     *
     * @param shim the birth round migration shim to bind
     */
    public void bind(@NonNull final BirthRoundMigrationShim shim) {
        ((BindableInputWire<GossipEvent, GossipEvent>) eventInput).bind(shim::migrateEvent);
    }
}
