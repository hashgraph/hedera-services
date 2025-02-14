// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test.consensus;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.WeightGenerator;
import edu.umd.cs.findbugs.annotations.NonNull;

public record ConsensusTestParams(
        @NonNull PlatformContext platformContext,
        int numNodes,
        @NonNull WeightGenerator weightGenerator,
        @NonNull String weightDesc,
        long... seeds) {
    @Override
    public String toString() {
        return numNodes + " nodes, " + weightDesc;
    }
}
