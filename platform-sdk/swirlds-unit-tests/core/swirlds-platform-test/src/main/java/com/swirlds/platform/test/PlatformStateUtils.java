// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.test;

import static com.swirlds.common.test.fixtures.RandomUtils.nextInt;
import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;

import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.BasicSoftwareVersion;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public final class PlatformStateUtils {

    private PlatformStateUtils() {}

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformStateModifier randomPlatformState(PlatformStateModifier platformState) {
        return randomPlatformState(new Random(), platformState);
    }

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformStateModifier randomPlatformState(final Random random, PlatformStateModifier platformState) {

        platformState.bulkUpdate(v -> {
            v.setLegacyRunningEventHash(randomHash(random));
            v.setRound(random.nextLong());
            v.setConsensusTimestamp(randomInstant(random));
            v.setCreationSoftwareVersion(new BasicSoftwareVersion(nextInt(1, 100)));
        });

        final List<MinimumJudgeInfo> minimumJudgeInfo = new LinkedList<>();
        for (int index = 0; index < 10; index++) {
            minimumJudgeInfo.add(new MinimumJudgeInfo(random.nextLong(), random.nextLong()));
        }
        platformState.setSnapshot(new ConsensusSnapshot(
                random.nextLong(),
                List.of(randomHash(random), randomHash(random), randomHash(random)),
                minimumJudgeInfo,
                random.nextLong(),
                randomInstant(random)));

        return platformState;
    }
}
