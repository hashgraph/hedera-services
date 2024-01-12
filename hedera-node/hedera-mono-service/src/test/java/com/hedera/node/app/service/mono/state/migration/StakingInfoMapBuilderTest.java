/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.address.AddressBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakingInfoMapBuilderTest {

    @Mock
    AddressBook addressBook;

    @Mock
    BootstrapProperties bootstrapProperties;

    @Test
    void buildsStakingInfoMapAsExpected() {
        final var totalHbar = 5_000_000_000L;
        final var expectedMaxStakePerNode = 1_000_000_000L;
        final var expectedMinStakePerNode = 500_000_000L;

        given(addressBook.getSize()).willReturn(5);
        given(addressBook.getNodeId(0)).willReturn(new NodeId(0));
        given(addressBook.getNodeId(1)).willReturn(new NodeId(1));
        given(addressBook.getNodeId(2)).willReturn(new NodeId(2));
        given(addressBook.getNodeId(3)).willReturn(new NodeId(3));
        given(addressBook.getNodeId(4)).willReturn(new NodeId(4));
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT)).willReturn(totalHbar);

        final var stakingInfoMap = buildStakingInfoMap(addressBook, bootstrapProperties);

        assertEquals(5, stakingInfoMap.size());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(0)));
        assertEquals(
                expectedMaxStakePerNode,
                stakingInfoMap.get(EntityNum.fromInt(0)).getMaxStake());
        assertEquals(
                expectedMinStakePerNode,
                stakingInfoMap.get(EntityNum.fromInt(0)).getMinStake());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(1)));
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(2)));
        assertEquals(
                expectedMaxStakePerNode,
                stakingInfoMap.get(EntityNum.fromInt(2)).getMaxStake());
        assertEquals(
                expectedMinStakePerNode,
                stakingInfoMap.get(EntityNum.fromInt(2)).getMinStake());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(3)));
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(4)));
    }
}
