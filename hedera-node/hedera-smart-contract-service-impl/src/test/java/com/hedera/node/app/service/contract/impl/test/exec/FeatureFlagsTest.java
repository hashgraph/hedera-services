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

package com.hedera.node.app.service.contract.impl.test.exec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.streams.SidecarType;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FeatureFlagsTest {
    @Mock
    private MessageFrame frame;

    @Test
    void sidecarsDefaultToTrue() {
        final var subject = mock(FeatureFlags.class);
        doCallRealMethod().when(subject).isSidecarEnabled(any(), any());
        assertTrue(subject.isSidecarEnabled(frame, SidecarType.CONTRACT_BYTECODE));
        assertTrue(subject.isSidecarEnabled(frame, SidecarType.CONTRACT_ACTION));
        assertTrue(subject.isSidecarEnabled(frame, SidecarType.CONTRACT_STATE_CHANGE));
    }
}
