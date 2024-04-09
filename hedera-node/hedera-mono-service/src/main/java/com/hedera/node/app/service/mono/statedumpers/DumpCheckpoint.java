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

package com.hedera.node.app.service.mono.statedumpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Enumerates the checkpoints in a PCLI-driven event stream replay at
 * which a dump of the network state can be taken.
 */
public enum DumpCheckpoint {
    /**
     * Immediately after loading a mono-service saved state, before
     * any migration has been done or events have been replayed.
     */
    MONO_PRE_MIGRATION,
    /**
     * After migrating from a mono-service saved state (so with a
     * mod-service state); but before any events have been replayed.
     */
    MOD_POST_MIGRATION,
    /**
     * After both migrating a mono-service saved state and replaying
     * the event stream.
     */
    MOD_POST_EVENT_STREAM_REPLAY,
    MONO_POST_EVENT_STREAM_REPLAY;

    /**
     * Returns the checkpoints selected by the {@code dumpCheckpoints}
     * system property, if set.
     *
     * @return the selected checkpoints, or an empty set if none are selected
     */
    public static Set<DumpCheckpoint> selectedDumpCheckpoints() {
        if (checkpoints == null) {
            final var literalSelection =
                    Optional.ofNullable(System.getProperty("dumpCheckpoints")).orElse("");
            checkpoints = literalSelection.isEmpty()
                    ? Collections.emptySet()
                    : Arrays.stream(literalSelection.split(","))
                            .map(DumpCheckpoint::valueOf)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(DumpCheckpoint.class)));
            System.out.println("Dumping at checkpoints: " + checkpoints);
        }
        return checkpoints;
    }

    private static Set<DumpCheckpoint> checkpoints = null;
}
