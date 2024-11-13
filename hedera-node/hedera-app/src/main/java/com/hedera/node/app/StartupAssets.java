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

package com.hedera.node.app;

import com.hedera.hapi.node.state.network.Network;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Optional;

public interface StartupAssets {
    interface Factory {
        StartupAssets fromInitialConditions(@NonNull Path workingDir);
    }

    /**
     * Called by a node that finds itself with an empty RosterService
     * state, and is thus at the migration boundary for adoption of the
     * proposed roster; implementations must either throw unsupported or
     * return an aggregation of the information in legacy config.txt and
     * public.pfx files.
     */
    Network migrationNetworkOrThrow();

    /**
     * Called by a node that finds itself with a completely empty state
     * and no genesis-config.txt file.
     */
    Network genesisNetworkOrThrow();

    /**
     * Returns a Network description if there is an override-config.txt
     * on disk that has not already been used in an earlier round than the
     * given number.
     */
    Optional<Network> overrideNetwork(long roundNumber);

    void archiveInitialConditions();
}
