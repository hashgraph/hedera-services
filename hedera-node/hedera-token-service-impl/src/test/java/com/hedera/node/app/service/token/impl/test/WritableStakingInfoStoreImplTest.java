/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test;

import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.impl.WritableStakingInfoStore;
import com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link WritableStakingInfoStore}.
 */
public class WritableStakingInfoStoreImplTest {
    /**
     * Node ID 1.
     */
    public static final EntityNumber NODE_ID_1 =
            EntityNumber.newBuilder().number(1L).build();

    private WritableStakingInfoStore subject;

    @BeforeEach
    void setUp() {
        final var wrappedState = MapWritableKVState.<EntityNumber, StakingNodeInfo>builder(
                        V0490TokenSchema.STAKING_INFO_KEY)
                .value(
                        NODE_ID_1,
                        StakingNodeInfo.newBuilder()
                                .nodeNumber(NODE_ID_1.number())
                                .stake(25)
                                .stakeRewardStart(15)
                                .unclaimedStakeRewardStart(5)
                                .build())
                .build();
        subject = new WritableStakingInfoStore(
                new MapWritableStates(Map.of(V0490TokenSchema.STAKING_INFO_KEY, wrappedState)));
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void constructorWithNullArg() {
        Assertions.assertThatThrownBy(() -> new WritableStakingInfoStore(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void constructorWithNonNullArg() {
        Assertions.assertThatCode(() -> new WritableStakingInfoStore(mock(WritableStates.class)))
                .doesNotThrowAnyException();
    }

    @Test
    void getForModifyNodeIdNotFound() {
        Assertions.assertThat(subject.get(-1)).isNull();
        Assertions.assertThat(subject.get(NODE_ID_1.number() + 1)).isNull();
    }

    @Test
    void getForModifyInfoFound() {
        Assertions.assertThat(subject.get(NODE_ID_1.number())).isNotNull().isInstanceOf(StakingNodeInfo.class);
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void putWithNullArg() {
        Assertions.assertThatThrownBy(() -> subject.put(NODE_ID_1.number() + 1, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void putSuccess() {
        final var newNodeId = NODE_ID_1.number() + 1;
        final var newStakingInfo =
                StakingNodeInfo.newBuilder().nodeNumber(newNodeId).stake(20).build();
        subject.put(newNodeId, newStakingInfo);

        Assertions.assertThat(subject.get(2)).isEqualTo(newStakingInfo);
    }
}
