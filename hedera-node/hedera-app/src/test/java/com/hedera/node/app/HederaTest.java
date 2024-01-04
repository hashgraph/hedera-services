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

package com.hedera.node.app;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.STAKING_INFO_KEY;
import static com.hedera.node.app.state.merkle.AddresBookUtils.createPretendBookFrom;
import static com.swirlds.common.constructable.ConstructableRegistryFactory.createConstructableRegistry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.StakingNodeInfo;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hedera.node.app.spi.fixtures.state.MapReadableStates;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hedera.node.app.state.merkle.MerkleTestBase;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.Platform;
import java.util.Iterator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class HederaTest extends MerkleTestBase {
    // Constructor: null registry throws
    // Constructor: bootstrap props throws
    // Constructor: Null version throws (pass the version in)
    // Constructor: #getSoftwareVersion returns the supplied version
    // Constructor: Verify constructable registry is used for registering MerkleHederaState (and for services)
    // Constructor: Verify constructable registry that throws is handled (for services and for MerkleHederaState)

    // TRY: Sending customized bootstrap props
    // TRY: Check logs?

    // newState: Called when there is no saved state, and when there is a saved state

    // onStateInitialized: Called when there is no saved state, and when there is a saved state
    // onStateInitialized: deserializedVersion is < current version
    // onStateInitialized: deserializedVersion is = current version
    // onStateInitialized: deserializedVersion is > current version
    // onStateInitialized: called for genesis
    // onStateInitialized: called for restart
    // onStateInitialized: called for reconnect (no error)
    // onStateInitialized: called for event stream recovery (no error)

    // onMigrate ONLY CALLED if deserializedVersion < current version or if genesis

    // init: Try to init with the system with something other than UTF-8 as the native charset (throws)
    // init: Try to init with sha384 not available (throws)
    // init: ensure JVM is set to UTF-8 after init
    // init: validateLedgerState ....
    // init: configurePlatform ....
    // init: exportAccountsIfDesired ....

    // run: start grpc server for old port
    // run: start grpc server for new port

    // genesis: onMigrate is called
    // genesis: seqStart comes from bootstrap props
    // genesis: results of createSpecialGenesisChildren
    // genesis: ... dagger ...
    // genesis: initializeFeeManager
    // genesis: initializeExchangeRateManager
    // genesis: initializeThrottleManager
    // genesis: version info is saved in state (and committed)
    // genesis: other stuff?....
    // genesis: updateStakeDetails....?
    // genesis: markPostUpradeScanStatus?....

    // createSpecialGenesisChildren: What if seqStart is < 0?
    // createSpecialGenesisChildren: other children...?

    // restart: update if needed
    // restart: dagger ....?
    // restart: housekeeping? freeze? etc.

    // rebuild aliases?

    @Mock
    private Platform platform;

    @Mock
    private PlatformContext platformContext;

    @Mock
    private MerkleHederaState merkleHederaState;

    @Mock
    private MapReadableStates readableStates;

    @Mock
    private MapReadableKVState<EntityNumber, StakingNodeInfo> mockReadableKVState;

    @Mock
    private Iterator<EntityNumber> mockIterator;

    private ConstructableRegistry constructableRegistry = createConstructableRegistry();
    private Hedera hedera = new Hedera(constructableRegistry);

    @BeforeEach
    void setUp() {
        given(merkleHederaState.createReadableStates(TokenService.NAME)).willReturn(readableStates);
        final var readableStakingNodes = MapReadableKVState.<EntityNumber, StakingNodeInfo>builder(STAKING_INFO_KEY)
                .value(
                        EntityNumber.newBuilder().number(0L).build(),
                        StakingNodeInfo.newBuilder().nodeNumber(0L).build())
                .value(
                        EntityNumber.newBuilder().number(1L).build(),
                        StakingNodeInfo.newBuilder().nodeNumber(1L).build())
                .build();
        given(readableStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY))
                .willReturn(readableStakingNodes);
        lenient().when(mockReadableKVState.keys()).thenReturn(mockIterator);
    }

    @Nested
    @DisplayName("Handling updateWeight Tests")
    final class UpdateWeightTest {
        @Test
        void updatesAddressBookWithZeroWeightOnGenesisStart() {
            final var node0 = new NodeId(0);
            final var node1 = new NodeId(1);
            given(platform.getSelfId()).willReturn(node0);
            given(platform.getContext()).willReturn(platformContext);

            final var pretendAddressBook = createPretendBookFrom(platform, true);

            assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

            hedera.onUpdateWeight(merkleHederaState, pretendAddressBook, platform.getContext());

            // if staking info map has node with 0 weight and a new node is added,
            // both gets weight of 0
            assertEquals(0L, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
        }

        @Test
        void updatesAddressBookWithZeroWeightForNewNodes() {
            final var node0 = new NodeId(0);
            final var node1 = new NodeId(1);
            given(platform.getSelfId()).willReturn(node0);
            given(platform.getContext()).willReturn(platformContext);

            final var pretendAddressBook = createPretendBookFrom(platform, true);
            final var readableStakingNodes = MapReadableKVState.<EntityNumber, StakingNodeInfo>builder(STAKING_INFO_KEY)
                    .value(
                            EntityNumber.newBuilder().number(0L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(500)
                                    .build())
                    .build();
            given(readableStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY))
                    .willReturn(readableStakingNodes);

            assertEquals(
                    1000L,
                    readableStakingNodes
                            .get(EntityNumber.newBuilder().number(0L).build())
                            .stake());

            assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

            hedera.onUpdateWeight(merkleHederaState, pretendAddressBook, platform.getContext());

            // if staking info map has node with 0 weight and a new node is added,
            // new nodes gets weight of 0
            assertEquals(500, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(0L, pretendAddressBook.getAddress(node1).getWeight());
        }

        @Test
        void updatesAddressBookWithNonZeroWeightsOnGenesisStartIfStakesExist() {
            final var node0 = new NodeId(0);
            final var node1 = new NodeId(1);
            given(platform.getSelfId()).willReturn(node0);
            given(platform.getContext()).willReturn(platformContext);

            final var pretendAddressBook = createPretendBookFrom(platform, true);

            final var readableStakingNodes = MapReadableKVState.<EntityNumber, StakingNodeInfo>builder(STAKING_INFO_KEY)
                    .value(
                            EntityNumber.newBuilder().number(0L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(0L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build())
                    .value(
                            EntityNumber.newBuilder().number(1L).build(),
                            StakingNodeInfo.newBuilder()
                                    .nodeNumber(1L)
                                    .stake(1000L)
                                    .weight(250)
                                    .build())
                    .build();
            given(readableStates.<EntityNumber, StakingNodeInfo>get(STAKING_INFO_KEY))
                    .willReturn(readableStakingNodes);

            assertEquals(
                    1000L,
                    readableStakingNodes
                            .get(EntityNumber.newBuilder().number(0L).build())
                            .stake());
            assertEquals(
                    1000L,
                    readableStakingNodes
                            .get(EntityNumber.newBuilder().number(1L).build())
                            .stake());

            assertEquals(10L, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(10L, pretendAddressBook.getAddress(node1).getWeight());

            hedera.onUpdateWeight(merkleHederaState, pretendAddressBook, platform.getContext());

            // if staking info map has node with 250L weight and a new node is added,
            // both gets weight of 250L
            assertEquals(250L, pretendAddressBook.getAddress(node0).getWeight());
            assertEquals(250L, pretendAddressBook.getAddress(node1).getWeight());
        }
    }
}
