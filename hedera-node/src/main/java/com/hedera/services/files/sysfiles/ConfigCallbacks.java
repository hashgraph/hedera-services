/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files.sysfiles;

import static com.hedera.services.context.properties.PropertyNames.*;

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class ConfigCallbacks {
    private static final Logger log = LogManager.getLogger(ConfigCallbacks.class);
    private static final long DEFAULT_MAX_TO_MIN_STAKE_RATIO = 4L;
    private final PropertySource properties;
    private final PropertySources propertySources;
    private final HapiOpPermissions hapiOpPermissions;
    private final Supplier<AddressBook> addressBook;
    private final GlobalDynamicProperties dynamicProps;
    private final ExpiryThrottle expiryThrottle;
    private final FunctionalityThrottling hapiThrottling;
    private final FunctionalityThrottling handleThrottling;
    private final FunctionalityThrottling scheduleThrottling;
    private final Supplier<MerkleNetworkContext> networkCtx;
    private final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos;

    @Inject
    public ConfigCallbacks(
            final HapiOpPermissions hapiOpPermissions,
            final GlobalDynamicProperties dynamicProps,
            final PropertySources propertySources,
            final ExpiryThrottle expiryThrottle,
            final @HapiThrottle FunctionalityThrottling hapiThrottling,
            final @HandleThrottle FunctionalityThrottling handleThrottling,
            final @ScheduleThrottle FunctionalityThrottling scheduleThrottling,
            final Supplier<AddressBook> addressBook,
            final @CompositeProps PropertySource properties,
            final Supplier<MerkleNetworkContext> networkCtx,
            final Supplier<MerkleMap<EntityNum, MerkleStakingInfo>> stakingInfos) {
        this.dynamicProps = dynamicProps;
        this.propertySources = propertySources;
        this.hapiOpPermissions = hapiOpPermissions;
        this.expiryThrottle = expiryThrottle;
        this.hapiThrottling = hapiThrottling;
        this.handleThrottling = handleThrottling;
        this.scheduleThrottling = scheduleThrottling;
        this.networkCtx = networkCtx;
        this.stakingInfos = stakingInfos;
        this.addressBook = addressBook;
        this.properties = properties;
    }

    public Consumer<ServicesConfigurationList> propertiesCb() {
        return config -> {
            propertySources.reloadFrom(config);
            dynamicProps.reload();
            hapiThrottling.applyGasConfig();
            handleThrottling.applyGasConfig();
            scheduleThrottling.applyGasConfig();
            expiryThrottle.rebuildGiven(
                    properties.getStringProperty(EXPIRY_THROTTLE_RESOURCE),
                    properties.getAccessListProperty(EXPIRY_MIN_CYCLE_ENTRY_CAPACITY));
            networkCtx.get().renumberBlocksToMatch(dynamicProps.knownBlockValues());
            updateMinAndMaxStakesWith(
                    properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT),
                    addressBook.get().getSize(),
                    dynamicProps.nodeMaxMinStakeRatios());
        };
    }

    private void updateMinAndMaxStakesWith(
            final long hbarFloat, final int numNodes, final Map<Long, Long> maxToMinStakeRatios) {
        final var maxStake = hbarFloat / numNodes;
        final var curStakingInfos = stakingInfos.get();
        curStakingInfos
                .keySet()
                .forEach(
                        num -> {
                            final var mutableInfo = curStakingInfos.getForModify(num);
                            mutableInfo.setMaxStake(maxStake);
                            final var maxToMinRatio =
                                    maxToMinStakeRatios.getOrDefault(
                                            num.longValue(), DEFAULT_MAX_TO_MIN_STAKE_RATIO);
                            final var minStake = maxStake / maxToMinRatio;
                            mutableInfo.setMinStake(minStake);
                            log.info(
                                    "Set node{} max/min stake to {}/{} ~ {}:1 ratio",
                                    num::longValue,
                                    mutableInfo::getMaxStake,
                                    mutableInfo::getMinStake,
                                    () -> maxToMinRatio);
                        });
    }

    public Consumer<ServicesConfigurationList> permissionsCb() {
        return hapiOpPermissions::reloadFrom;
    }
}
