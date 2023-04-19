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

import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.common.test.RandomUtils.randomInstant;
import static com.swirlds.platform.test.event.RandomEventUtils.randomEvent;

import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.RandomAddressBookGenerator;
import com.swirlds.common.test.RandomAddressBookGenerator.WeightDistributionStrategy;
import com.swirlds.platform.internal.EventImpl;
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
    public static PlatformState randomPlatformState(boolean generateEventList) {
        return randomPlatformState(new Random(), generateEventList);
    }

    /**
     * Generate a randomized PlatformState object. Values contained internally may be nonsensical.
     */
    public static PlatformState randomPlatformState(final Random random, boolean generateEventList) {
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
        platformData.setNumEventsCons(random.nextLong());
        platformData.setConsensusTimestamp(randomInstant(random));
        platformData.setEvents(new EventImpl[0]);
        platformData.setMinGenInfo(List.of());

        if (generateEventList) {
            final EventImpl[] events = new EventImpl[10];
            for (int index = 0; index < events.length; index++) {
                events[index] = randomEvent(random, random.nextInt(), null, null);
            }
            platformState.getPlatformData().setEvents(events);
        }

        platformState.getPlatformData().setLastTransactionTimestamp(randomInstant(random));

        final List<MinGenInfo> minGenInfo = new LinkedList<>();
        for (int index = 0; index < 10; index++) {
            minGenInfo.add(new MinGenInfo(random.nextLong(), random.nextLong()));
        }
        platformState.getPlatformData().setMinGenInfo(minGenInfo);

        return platformState;
    }
}
