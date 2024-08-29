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

package com.hedera.node.app.throttle;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Default implementation of {@link ThrottleAdviser}.
 */
public class AppThrottleAdviser implements ThrottleAdviser {

    private final NetworkUtilizationManager networkUtilizationManager;
    private final Instant consensusNow;

    public AppThrottleAdviser(
            @NonNull final NetworkUtilizationManager networkUtilizationManager, @NonNull final Instant consensusNow) {
        this.networkUtilizationManager = requireNonNull(networkUtilizationManager);
        this.consensusNow = requireNonNull(consensusNow);
    }

    @Override
    public boolean shouldThrottleNOfUnscaled(int n, @NonNull final HederaFunctionality function) {
        requireNonNull(function);
        return networkUtilizationManager.shouldThrottleNOfUnscaled(n, function, consensusNow);
    }
}
