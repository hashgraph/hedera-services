/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state;

import static com.hedera.node.app.service.token.impl.schemas.V0490TokenSchema.STAKING_INFO_KEY;
import static com.swirlds.platform.test.fixtures.addressbook.AddresBookUtils.createPretendBookFrom;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.Hedera;
import com.hedera.node.app.service.token.TokenService;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.test.fixtures.state.MerkleTestBase;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.vmapsupport.OnDiskKey;
import com.swirlds.state.merkle.vmapsupport.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerkleStateLifecyclesImplTest extends MerkleTestBase {
    private static final int PRETEND_STAKING_INFO_CHILD_INDEX = 7;

    @Mock
    private Hedera hedera;

    @Mock
    private Event event;

    @Mock
    private Round round;

    @Mock
    private Platform platform;

    @Mock
    private PlatformContext platformContext;

    @Mock
    private MerkleStateRoot merkleStateRoot;

    @Mock
    private BiConsumer<
                    VirtualMap<OnDiskKey<EntityNumber>, OnDiskValue<StakingNodeInfo>>,
                    BiConsumer<EntityNumber, StakingNodeInfo>>
            weightUpdateVisitor;

    private MerkleStateLifecyclesImpl subject;

    @BeforeEach
    void setUp() {
        subject = new MerkleStateLifecyclesImpl(hedera, weightUpdateVisitor);
    }

    @Test
    void delegatesOnPreHandle() {
        subject.onPreHandle(event, merkleStateRoot);

        verify(hedera).onPreHandle(event, merkleStateRoot);
    }

    @Test
    void delegatesOnHandleConsensusRound() {
        subject.onHandleConsensusRound(round, merkleStateRoot);

        verify(hedera).onHandleConsensusRound(round, merkleStateRoot);
    }

    @Test
    void delegatesOnSealConsensusRound() {
        subject.onSealConsensusRound(round, merkleStateRoot);

        verify(hedera).onSealConsensusRound(round, merkleStateRoot);
    }

    @Test
    void delegatesOnStateInitialized() {
        subject.onStateInitialized(merkleStateRoot, platform, InitTrigger.GENESIS, null);

        verify(hedera).onStateInitialized(merkleStateRoot, platform, InitTrigger.GENESIS);
    }

    @Test
    void updatesAddressBookWithZeroWeightOnGenesisStart() {
        final var node0 = NodeId.of(0);
        final var node1 = NodeId.of(1);
        given(platform.getSelfId()).willReturn(node0);

        final var pretendAddressBook = createPretendBookFrom(platform, true);

        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.onUpdateWeight(merkleStateRoot, pretendAddressBook, platform.getContext());

        // if staking info map has node with 0 weight and a new node is added,
        // both gets weight of 0
        assertEquals(0L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
    }

    @Test
    void updatesAddressBookWithZeroWeightForNewNodes() {
        final var node0 = NodeId.of(0);
        final var node1 = NodeId.of(1);
        given(platform.getSelfId()).willReturn(node0);
        final var pretendAddressBook = createPretendBookFrom(platform, true);
        given(merkleStateRoot.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY))
                .willReturn(PRETEND_STAKING_INFO_CHILD_INDEX);
        doAnswer(invocationOnMock -> {
                    final BiConsumer<EntityNumber, StakingNodeInfo> visitor = invocationOnMock.getArgument(1);
                    visitor.accept(
                            EntityNumber.newBuilder().number(0L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(500)
                                    .build());
                    return null;
                })
                .when(weightUpdateVisitor)
                .accept(any(), any());

        subject.onUpdateWeight(merkleStateRoot, pretendAddressBook, platform.getContext());

        // if staking info map has node with 0 weight and a new node is added,
        // new nodes gets weight of 0
        assertEquals(500, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
    }

    @Test
    void doesntUpdateAddressBookIfNodeIdFromStateDoesntExist() {
        final var node0 = NodeId.of(0);
        final var node1 = NodeId.of(1);
        final var node2 = NodeId.of(2);
        given(platform.getSelfId()).willReturn(node0);

        final var pretendAddressBook = createPretendBookFrom(platform, true);
        given(merkleStateRoot.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY))
                .willReturn(PRETEND_STAKING_INFO_CHILD_INDEX);
        doAnswer(invocationOnMock -> {
                    final BiConsumer<EntityNumber, StakingNodeInfo> visitor = invocationOnMock.getArgument(1);
                    visitor.accept(
                            EntityNumber.newBuilder().number(0L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(100)
                                    .build());
                    visitor.accept(
                            EntityNumber.newBuilder().number(1L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(1L)
                                    .stake(1000L)
                                    .weight(200)
                                    .build());
                    visitor.accept(
                            EntityNumber.newBuilder().number(2L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(2L)
                                    .stake(1000L)
                                    .weight(200)
                                    .build());
                    return null;
                })
                .when(weightUpdateVisitor)
                .accept(any(), any());
        // there is no node2 in the addressBook
        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());
        assertThrows(NoSuchElementException.class, () -> pretendAddressBook.getAddress(node2));

        assertDoesNotThrow(() -> subject.onUpdateWeight(merkleStateRoot, pretendAddressBook, platform.getContext()));

        // if staking info map has node with 0 weight and a new node is added,
        // new nodes gets weight of 0
        assertEquals(100, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(200, pretendAddressBook.getAddress(node1).getWeight());
        assertThrows(NoSuchElementException.class, () -> pretendAddressBook.getAddress(node2));
    }

    @Test
    void updatesAddressBookWithNonZeroWeightsOnGenesisStartIfStakesExist() {
        final var node0 = NodeId.of(0);
        final var node1 = NodeId.of(1);
        given(platform.getSelfId()).willReturn(node0);

        final var pretendAddressBook = createPretendBookFrom(platform, true);
        given(merkleStateRoot.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY))
                .willReturn(PRETEND_STAKING_INFO_CHILD_INDEX);
        doAnswer(invocationOnMock -> {
                    final BiConsumer<EntityNumber, StakingNodeInfo> visitor = invocationOnMock.getArgument(1);
                    visitor.accept(
                            EntityNumber.newBuilder().number(0L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build());
                    visitor.accept(
                            EntityNumber.newBuilder().number(1L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build());
                    return null;
                })
                .when(weightUpdateVisitor)
                .accept(any(), any());

        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.onUpdateWeight(merkleStateRoot, pretendAddressBook, platform.getContext());

        // if staking info map has node with 250L weight and a new node is added,
        // both gets weight of 250L
        assertEquals(250L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(250L, pretendAddressBook.getAddress(node1).getWeight());
    }

    @Test
    void marksNonExistingNodesToDeletedInStateAndAddsNewNodesToState() {
        given(merkleStateRoot.findNodeIndex(TokenService.NAME, STAKING_INFO_KEY))
                .willReturn(PRETEND_STAKING_INFO_CHILD_INDEX);
        doAnswer(invocationOnMock -> {
                    final BiConsumer<EntityNumber, StakingNodeInfo> visitor = invocationOnMock.getArgument(1);
                    visitor.accept(
                            EntityNumber.newBuilder().number(2L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(2L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build());
                    visitor.accept(
                            EntityNumber.newBuilder().number(1L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(1L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build());
                    return null;
                })
                .when(weightUpdateVisitor)
                .accept(any(), any());

        final var node0 = NodeId.of(0);
        final var node1 = NodeId.of(1);

        given(platform.getSelfId()).willReturn(node0);

        // platform addressBook has nodes 0, 1
        final var pretendAddressBook = createPretendBookFrom(platform, true);
        // stakingInfo has 1, 2
        assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

        subject.onUpdateWeight(merkleStateRoot, pretendAddressBook, platformContext);

        // node 0 is added so the weight of new node is 0
        // node 1 weight will be updated
        // node 2 is deleted so , it is marked deleted weight will be 0

        assertEquals(0L, pretendAddressBook.getAddress(node0).getWeight());
        assertEquals(250L, pretendAddressBook.getAddress(node1).getWeight());
    }
}
