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

package com.swirlds.platform.state;

import com.swirlds.common.system.InitTrigger;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.uptime.UptimeData;
import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * Responsible for building the genesis state.
 */
public final class GenesisStateBuilder {

    private GenesisStateBuilder() {}

    /**
     * Construct a genesis platform state.
     *
     * @return a genesis platform state
     */
    private static PlatformState buildGenesisPlatformState(
            final AddressBook addressBook, final SoftwareVersion appVersion) {

        final PlatformState platformState = new PlatformState();
        final PlatformData platformData = new PlatformData();
        platformState.setPlatformData(platformData);
        platformState.setAddressBook(addressBook.copy());
        platformState.setUptimeData(new UptimeData());

        platformData.setCreationSoftwareVersion(appVersion);
        platformData.setRound(0);
        platformData.setNumEventsCons(0);
        platformData.setHashEventsCons(null);
        platformData.setEvents(new EventImpl[0]);
        platformData.setMinGenInfo(List.of());
        platformData.setLastTransactionTimestamp(Instant.ofEpochSecond(0L));
        platformData.setEpochHash(null);
        platformData.setConsensusTimestamp(Instant.ofEpochSecond(0L));

        return platformState;
    }

    /**
     * Construct a genesis dual state.
     *
     * @param configuration configuration for the platform
     * @return a genesis dual state
     */
    private static DualStateImpl buildGenesisDualState(final Configuration configuration) {
        final DualStateImpl dualState = new DualStateImpl();

        final long genesisFreezeTime = configuration.getValue("genesisFreezeTime", Long.class, 0L);
        if (genesisFreezeTime > 0) {
            dualState.setFreezeTime(Instant.ofEpochSecond(genesisFreezeTime));
        }

        return dualState;
    }

    /**
     * Build and initialize a genesis state.
     *
     * @param platform                  the platform running this node
     * @param addressBook               the current address book
     * @param appVersion                the software version of the app
     * @param genesisSwirldStateBuilder builds the genesis application state
     * @return a genesis state
     */
    public static State buildGenesisState(
            final Platform platform,
            final AddressBook addressBook,
            final SoftwareVersion appVersion,
            final Supplier<SwirldState> genesisSwirldStateBuilder) {

        final State state = new State();
        state.setPlatformState(buildGenesisPlatformState(addressBook, appVersion));
        state.setSwirldState(genesisSwirldStateBuilder.get());
        state.setDualState(buildGenesisDualState(platform.getContext().getConfiguration()));

        state.getSwirldState()
                .init(platform, state.getSwirldDualState(), InitTrigger.GENESIS, SoftwareVersion.NO_VERSION);
        state.markAsInitialized();

        return state;
    }
}
