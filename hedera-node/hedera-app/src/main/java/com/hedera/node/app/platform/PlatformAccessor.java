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

package com.hedera.node.app.platform;

import com.swirlds.common.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A singleton class that provides access to the {@link com.swirlds.common.system.Platform}.
 */
@Singleton
public class PlatformAccessor {
    private Platform platform = null;

    @Inject
    public PlatformAccessor() {
        // Default constructor
    }

    /**
     * Returns the {@link Platform}.
     *
     * @return the {@link Platform}.
     */
    @Nullable
    public Platform getPlatform() {
        return platform;
    }

    /**
     * Sets the {@link Platform}.
     *
     * @param platform the {@link Platform}.
     */
    public void setPlatform(@NonNull Platform platform) {
        this.platform = Objects.requireNonNull(platform);
    }
}
