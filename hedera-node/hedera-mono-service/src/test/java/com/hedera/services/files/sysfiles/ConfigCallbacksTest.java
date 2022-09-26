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
import static com.hedera.services.throttling.MapAccessType.ACCOUNTS_GET;
import static com.hedera.services.throttling.MapAccessType.STORAGE_PUT;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.times;

import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.sysfiles.domain.KnownBlockValues;
import com.hedera.services.throttling.ExpiryThrottle;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.MapAccessType;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConfigCallbacksTest {

    private static final long node0MaxStake = 100L;
    private static final long node1MaxStake = 200L;
    private static final long node2MaxStake = 300L;
    private static final String literalBlockValues =
            "c9e37a7a454638ca62662bd1a06de49ef40b3444203fe329bbc81363604ea7f8@666";
    private static final List<MapAccessType> minReqUnitOfWork = List.of(ACCOUNTS_GET, STORAGE_PUT);
    private static final KnownBlockValues blockValues = KnownBlockValues.from(literalBlockValues);

    @Mock private ExpiryThrottle expiryThrottle;
    @Mock private AddressBook addressBook;
    @Mock private GlobalDynamicProperties dynamicProps;
    @Mock private PropertySources propertySources;
    @Mock private HapiOpPermissions hapiOpPermissions;
    @Mock private FunctionalityThrottling functionalityThrottling;
    @Mock private MerkleNetworkContext networkCtx;
    @Mock private PropertySource properties;

    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos = new MerkleMap<>();
    private ConfigCallbacks subject;

    @BeforeEach
    void setUp() {
        subject =
                new ConfigCallbacks(
                        hapiOpPermissions,
                        dynamicProps,
                        propertySources,
                        expiryThrottle,
                        functionalityThrottling,
                        functionalityThrottling,
                        functionalityThrottling,
                        () -> addressBook,
                        properties,
                        () -> networkCtx,
                        () -> stakingInfos);
    }

    @Test
    void propertiesCbAsExpected() {
        final var numNodes = 10;
        final var hbarFloat = 50_000_000_000L * 100_000_000L;
        final var expiryResourceLoc = "something.json";
        givenWellKnownStakingInfos();
        given(properties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT)).willReturn(hbarFloat);
        given(properties.getStringProperty(EXPIRY_THROTTLE_RESOURCE)).willReturn(expiryResourceLoc);
        given(properties.getAccessListProperty(EXPIRY_MIN_CYCLE_ENTRY_CAPACITY))
                .willReturn(minReqUnitOfWork);
        given(addressBook.getSize()).willReturn(numNodes);
        given(dynamicProps.knownBlockValues()).willReturn(blockValues);
        given(dynamicProps.nodeMaxMinStakeRatios()).willReturn(Map.of(0L, 2L, 1L, 8L));
        final var overrideMaxStake = hbarFloat / numNodes;
        var config = ServicesConfigurationList.getDefaultInstance();

        // when:
        subject.propertiesCb().accept(config);

        // then:
        verify(propertySources).reloadFrom(config);
        verify(expiryThrottle).rebuildGiven(expiryResourceLoc, minReqUnitOfWork);
        verify(dynamicProps).reload();
        verify(functionalityThrottling, times(3)).applyGasConfig();
        verify(networkCtx).renumberBlocksToMatch(blockValues);
        // and:
        final var updatedNode0Info = stakingInfos.get(EntityNum.fromLong(0L));
        assertStakes(updatedNode0Info, overrideMaxStake / 2, overrideMaxStake);
        final var updatedNode1Info = stakingInfos.get(EntityNum.fromLong(1L));
        assertStakes(updatedNode1Info, overrideMaxStake / 8, overrideMaxStake);
        final var updatedNode2Info = stakingInfos.get(EntityNum.fromLong(2L));
        assertStakes(updatedNode2Info, overrideMaxStake / 4, overrideMaxStake);
    }

    @Test
    void permissionsCbAsExpected() {
        var config = ServicesConfigurationList.getDefaultInstance();

        // when:
        subject.permissionsCb().accept(config);

        // then:
        verify(hapiOpPermissions).reloadFrom(config);
    }

    private void assertStakes(
            final MerkleStakingInfo info, final long minStake, final long maxStake) {
        Assertions.assertEquals(minStake, info.getMinStake());
        Assertions.assertEquals(maxStake, info.getMaxStake());
    }

    private void givenWellKnownStakingInfos() {
        stakingInfos.put(EntityNum.fromLong(0L), infoWith(node0MaxStake));
        stakingInfos.put(EntityNum.fromLong(1L), infoWith(node1MaxStake));
        stakingInfos.put(EntityNum.fromLong(2L), infoWith(node2MaxStake));
    }

    private MerkleStakingInfo infoWith(final long maxStake) {
        final var ans = new MerkleStakingInfo();
        ans.setMaxStake(maxStake);
        return ans;
    }
}
