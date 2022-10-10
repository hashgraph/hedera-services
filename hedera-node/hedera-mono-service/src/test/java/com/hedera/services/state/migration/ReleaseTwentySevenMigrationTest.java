/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.migration;

import static com.hedera.services.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReleaseTwentySevenMigrationTest {
    @Mock AddressBook addressBook;
    @Mock BootstrapProperties bootstrapProperties;

    @Test
    void buildsStakingInfoMapAsExpected() {
        final var address1 = mock(Address.class);
        final var address2 = mock(Address.class);
        final var address3 = mock(Address.class);
        final var address4 = mock(Address.class);
        final var address5 = mock(Address.class);
        final var totalHbar = 5_000_000_000L;
        final var expectedMaxStakePerNode = 1_000_000_000L;
        final var expectedMinStakePerNode = 500_000_000L;

        given(addressBook.getSize()).willReturn(5);
        given(addressBook.getAddress(0)).willReturn(address1);
        given(address1.getId()).willReturn(0L);
        given(addressBook.getAddress(1)).willReturn(address2);
        given(address2.getId()).willReturn(1L);
        given(addressBook.getAddress(2)).willReturn(address3);
        given(address3.getId()).willReturn(2L);
        given(addressBook.getAddress(3)).willReturn(address4);
        given(address4.getId()).willReturn(3L);
        given(addressBook.getAddress(4)).willReturn(address5);
        given(address5.getId()).willReturn(4L);
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT))
                .willReturn(totalHbar);

        var stakingInfoMap = buildStakingInfoMap(addressBook, bootstrapProperties);

        assertEquals(5, stakingInfoMap.size());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(0)));
        assertEquals(
                expectedMaxStakePerNode, stakingInfoMap.get(EntityNum.fromInt(0)).getMaxStake());
        assertEquals(
                expectedMinStakePerNode, stakingInfoMap.get(EntityNum.fromInt(0)).getMinStake());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(1)));
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(2)));
        assertEquals(
                expectedMaxStakePerNode, stakingInfoMap.get(EntityNum.fromInt(2)).getMaxStake());
        assertEquals(
                expectedMinStakePerNode, stakingInfoMap.get(EntityNum.fromInt(2)).getMinStake());
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(3)));
        assertTrue(stakingInfoMap.containsKey(EntityNum.fromInt(4)));
    }
}
