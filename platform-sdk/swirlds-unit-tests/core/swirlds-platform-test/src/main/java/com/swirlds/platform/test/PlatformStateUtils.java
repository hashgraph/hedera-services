/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import com.swirlds.common.test.fixtures.Randotron;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator.WeightDistributionStrategy;
import java.util.LinkedList;
import java.util.List;

public final class PlatformStateUtils {

    private PlatformStateUtils() {}

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformState randomPlatformState(final Randotron random) {
        final PlatformState platformState = new PlatformState();

        final AddressBook addressBook = new RandomAddressBookGenerator(random)
                .setSize(4)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        platformState.setAddressBook(addressBook);
        platformState.setLegacyRunningEventHash(random.randomHash());
        platformState.setRunningEventHash(random.randomHash());
        platformState.setRound(random.nextLong());
        platformState.setConsensusTimestamp(random.randomInstant());

        final List<MinimumJudgeInfo> minimumJudgeInfo = new LinkedList<>();
        for (int index = 0; index < 10; index++) {
            minimumJudgeInfo.add(new MinimumJudgeInfo(random.nextLong(), random.nextLong()));
        }
        platformState.setSnapshot(new ConsensusSnapshot(
                random.nextLong(),
                List.of(random.randomHash(), random.randomHash(), random.randomHash()),
                minimumJudgeInfo,
                random.nextLong(),
                random.randomInstant()));

        return platformState;
    }
}
