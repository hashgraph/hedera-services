/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static com.swirlds.common.test.fixtures.RandomUtils.randomHash;
import static com.swirlds.common.test.fixtures.RandomUtils.randomInstant;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.platform.consensus.ConsensusSnapshot;
import com.swirlds.platform.state.MinGenInfo;
import com.swirlds.platform.state.PlatformData;
import com.swirlds.platform.state.PlatformState;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public final class PlatformStateUtils {

    private PlatformStateUtils() {}

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformState randomPlatformState() {
        return randomPlatformState(new Random());
    }

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformState randomPlatformState(final Random random) {
        final PlatformState platformState = new PlatformState();
        final PlatformData platformData = new PlatformData();
        platformState.setPlatformData(platformData);

        final AddressBook addressBook = new RandomAddressBookGenerator()
                .setSize(4)
                .setWeightDistributionStrategy(WeightDistributionStrategy.BALANCED)
                .build();

        platformState.setAddressBook(addressBook);
        platformData.setHashEventsCons(randomHash(random));
        platformData.setRound(random.nextLong());
        platformData.setConsensusTimestamp(randomInstant(random));

        final List<MinGenInfo> minGenInfo = new LinkedList<>();
        for (int index = 0; index < 10; index++) {
            minGenInfo.add(new MinGenInfo(random.nextLong(), random.nextLong()));
        }
        platformState
                .getPlatformData()
                .setSnapshot(new ConsensusSnapshot(
                        random.nextLong(),
                        List.of(randomHash(random), randomHash(random), randomHash(random)),
                        minGenInfo,
                        random.nextLong(),
                        randomInstant(random)));

        return platformState;
    }
}
