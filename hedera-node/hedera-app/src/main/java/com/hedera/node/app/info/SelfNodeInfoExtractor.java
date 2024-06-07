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

package com.hedera.node.app.info;

import com.hedera.node.app.version.HederaSoftwareVersion;
import com.swirlds.platform.system.Platform;
import com.swirlds.state.spi.info.SelfNodeInfo;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Strategy for computing the self node info from a {@link com.swirlds.platform.system.Platform}
 * and {@link com.hedera.node.app.version.HederaSoftwareVersion}.
 */
@FunctionalInterface
public interface SelfNodeInfoExtractor {
    /**
     * Compute the self node info from the given platform and software version.
     *
     * @param platform the platform
     * @param softwareVersion the software version
     * @return the self node info
     */
    SelfNodeInfo extractSelfNodeInfo(@NonNull Platform platform, @NonNull HederaSoftwareVersion softwareVersion);
}
