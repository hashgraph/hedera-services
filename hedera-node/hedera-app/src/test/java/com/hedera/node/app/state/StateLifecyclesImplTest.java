/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.Hedera;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.test.fixtures.state.MerkleTestBase;
import com.swirlds.state.merkle.MerkleStateRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateLifecyclesImplTest extends MerkleTestBase {
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

    private StateLifecyclesImpl subject;

    @BeforeEach
    void setUp() {
        subject = new StateLifecyclesImpl(hedera);
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
    void onUpdateWeightIsNoop() {
        assertDoesNotThrow(() -> subject.onUpdateWeight(merkleStateRoot, mock(AddressBook.class), platformContext));
    }
}
