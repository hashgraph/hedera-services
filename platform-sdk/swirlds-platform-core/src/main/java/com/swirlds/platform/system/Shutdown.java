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

package com.swirlds.platform.system;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;

/**
 * A utility for shutting down the JVM.
 */
public final class Shutdown {

    public Shutdown() {}

    /**
     * Shut down the JVM.
     *
     * @param reason   the reason the JVM is being shut down
     * @param exitCode the exit code to return when the JVM has been shut down
     */
    public void shutdown(@Nullable final String reason, @NonNull final SystemExitCode exitCode) {
        Objects.requireNonNull(exitCode);
        SystemExitUtils.exitSystem(exitCode, reason);
    }
}
