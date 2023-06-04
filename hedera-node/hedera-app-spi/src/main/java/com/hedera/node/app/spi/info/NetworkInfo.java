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

package com.hedera.node.app.spi.info;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Provides information about the network.
 */
public interface NetworkInfo {

    /**
     * Returns the current ledger ID.
     *
     * @return the {@link Bytes} of the current ledger ID
     */
    @NonNull
    Bytes ledgerId();

    /**
     * Gets the current deployed version of software on the network
     *
     * @return The deployed version
     */
    @NonNull
    SemanticVersion servicesVersion();

    /**
     * Get the current deployed HAPI version running on the network.
     *
     * @return The HAPI version
     */
    @NonNull
    SemanticVersion hapiVersion();
}
