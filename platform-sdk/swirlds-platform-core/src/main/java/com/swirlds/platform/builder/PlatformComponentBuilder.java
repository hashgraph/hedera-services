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

package com.swirlds.platform.builder;

import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getGlobalMetrics;
import static com.swirlds.platform.builder.internal.StaticPlatformBuilder.getMetricsProvider;
import static com.swirlds.platform.gui.internal.BrowserWindowManager.getPlatforms;

import com.swirlds.platform.SwirldsPlatform;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.util.MetricsDocUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * The advanced platform builder is responsible for constructing platform components. This class is exposed so that
 * individual components can be replaced with alternate implementations.
 */
public class PlatformComponentBuilder {

    private final PlatformBuildingBlocks platformBuildingBlocks;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // All non-final variables defined here are components. In general, default components should not be passed to
    // other components as constructor arguments, and it should be legal to construct components in any order.

    // Future work: move all components here

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // All other variables should be defined below this line.

    /**
     * False if this builder has not yet been used to build a platform (or platform component builder), true if it has.
     */
    private boolean used;

    /**
     * Constructor.
     *
     * @param platformBuildingBlocks the build context for the platform under construction, contains all data needed to
     *                             construct platform components
     */
    public PlatformComponentBuilder(@NonNull final PlatformBuildingBlocks platformBuildingBlocks) {
        this.platformBuildingBlocks = Objects.requireNonNull(platformBuildingBlocks);
    }

    /**
     * Get the build context for this platform. Contains all data needed to construct platform components.
     *
     * @return the build context
     */
    @NonNull
    public PlatformBuildingBlocks getBuildingBlocks() {
        return platformBuildingBlocks;
    }

    /**
     * Throw an exception if this builder has been used to build a platform or a platform factory.
     */
    private void throwIfAlreadyUsed() {
        if (used) {
            throw new IllegalStateException("PlatformBuilder has already been used");
        }
    }

    /**
     * Build the platform.
     *
     * @return the platform
     */
    @NonNull
    public Platform build() {
        throwIfAlreadyUsed();
        used = true;

        try (final ReservedSignedState initialState = platformBuildingBlocks.initialState()) {
            return new SwirldsPlatform(this);
        } finally {

            // Future work: eliminate the static variables that require this code to exist
            if (platformBuildingBlocks.firstPlatform()) {
                MetricsDocUtils.writeMetricsDocumentToFile(
                        getGlobalMetrics(),
                        getPlatforms(),
                        platformBuildingBlocks.platformContext().getConfiguration());
                getMetricsProvider().start();
            }
        }
    }
}
