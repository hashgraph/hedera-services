/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state.query;

import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides the latest complete signed state, or null if none is available.
 */
public interface LatestSignedStateProvider {

    /**
     * Returns the latest complete (fully signed) state with a reservation that is released when the
     * {@link AutoCloseableWrapper} is closed.
     *
     * @param reason a short description of why this SignedState is being reserved. Each location where a SignedState is
     *               reserved should attempt to use a unique reason, as this makes debugging reservation bugs easier.
     * @return an auto-closeable with the latest complete state, or an auto-closeable wrapper with {@code null} if none
     * is available.
     */
    @NonNull
    ReservedSignedState getLatestSignedState(@NonNull final String reason);
}
