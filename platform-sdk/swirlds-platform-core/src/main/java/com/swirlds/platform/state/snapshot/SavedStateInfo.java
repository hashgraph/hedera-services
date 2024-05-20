/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.snapshot;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;

/**
 * A description of a signed state file and its associated round number.
 *
 * @param stateFile the path of the SignedState.swh file.
 * @param metadata  the metadata of the signed state
 */
public record SavedStateInfo(@NonNull Path stateFile, @NonNull SavedStateMetadata metadata) {

    /**
     * Get the parent directory.
     *
     * @return the parent directory
     */
    @NonNull
    public Path getDirectory() {
        return stateFile.toAbsolutePath().getParent();
    }
}
